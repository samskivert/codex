//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema}
import samscala.nexus.Entity
import scala.annotation.tailrec

import codex._
import codex.extract.{Extractor, ProjectModel, Visitor}

/** The source of information about a particular project. */
class Project(
  /** The human readable name of this project. */
  val name :String,
  /** The group part of this project's fqId. */
  val groupId :String,
  /** The artifact part of this project's fqId. */
  val artifactId :String,
  /** The version string for this project. */
  val version :String,
  /** This project's flavor (related to build system, e.g. m2, ivy, maven, sbt, etc.). */
  val flavor :String,
  /** When this project was imported into the library. */
  val imported :Long,
  /** The directory at which this project is rooted, as a string. */
  val rootPath :String
) extends KeyedEntity[Long] with Entity {
  import ProjectDB._

  /** A unique identifier for this project (1 or higher). */
  val id :Long = 0L

  /** The directory at which this project is rooted. */
  lazy val root = new File(rootPath)

  /** This project's fully qualified (aka Maven) id, or some approximation thereof. */
  lazy val fqId = FqId(groupId, artifactId, version)

  /** Returns all definitions in this project's extent with the specified name.
    *
    * @param name the name of the definition. If the name is all lower case, a case insensitive
    * match is performed, otherwise a case sensitive match is used. (TODO)
    * @param kinds a restriction on the kinds of definitions that will be returned. If empty, all
    * kinds will be returned.
    */
  def findDefn (name :String, kinds :Set[String] = Set()) :Iterable[Loc] =
    findDefn(name, kinds, p => l => l)

  /** Like the other `findDefn`, but which maps via `map` in the owning project's context. */
  def findDefn[T] (name :String, kinds :Set[String], map :(Project => Loc => T)) :Iterable[T] = {
    // if we've never been updated, do a blocking rescan (we only do such on top-level projects; we
    // let the findDefnLocal trigger a non-blocking rescan on our depends)
    if (lastIndexed == 0L) reindex()

    // TODO: handle forTest
    val deps = depends filterNot(_.forTest)
    log.info(s"${this.name} (and ${deps.size} depends) findDefn: $name")

    // TODO: return results incrementally?

    // search this project and its transitive depends (this will auto-create projects for our
    // transitive dependencies if they don't already exist)
    findDefnLocal(name, kinds, map, true) ++ projects.request { ps =>
      for { dh  <- deps flatMap ps.forDep
            loc <- dh request(_ findDefnLocal(name, kinds, map, false)) } yield loc
    }
    // TODO: report when we queued up rescans on projects so caller knows to retry on nada
  }

  /** Returns the fully qualified name of the supplied element.
    * TODO: currently assumes Java-like element separator (.).
    */
  def qualify (loc :Loc) :String = comps(loc).mkString(".")

  /** Returns (`loc`, `fqName`, `docPath`) for the supplied element. */
  def fordoc (loc :Loc) :(Loc, String, String) = {
    val cs = comps(loc)
    val docurl = cs flatMap(_ split("\\.")) mkString("/")
    // TODO: figure out a less hacky way of handling Scala objects
    val hackurl = loc.kind match {
      case "object" => docurl + "$"
      case _ => docurl
    }
    (loc, cs.mkString("."), s"/$flavor/$groupId/$artifactId/$version/$hackurl.html")
  }

  /** Requests that this project attempt to download its doc jar if it's never done so. */
  def checkdoc () {
    // TODO: who should be responsible for not repeatedly attempting to download non-existent docs?
    // maybe have the project model do it?
    if (!_model.hasDocs) {
      log.info("Trying docgen for checkdoc", "proj", fqId)
      _model.tryGenerateDocs()
    }
  }

  /** Used by [[visit]] to visit all comp units and elements in this project. */
  trait Viz {
    def onCompUnit (id :Int, path :String)
    def onElement (id :Int, ownerId :Int, name :String, kind :String, unitId :Int, offset :Int)
  }

  /** Visits all compunits and elements in this project, in a sensible order. Each compunit is
    * visited in turn and each top-level element in that compunit is visited, followed immediately
    * by its children elements, and so forth (a depth-first traversal).
    */
  def visit (viz :Viz) {
    val units = using(_session) { compunitsT.toList }
    val elems = using(_session) { elementsT.toSeq groupBy(_.unitId) }

    units.sortBy(_.path) foreach { u =>
      viz.onCompUnit(u.id, u.path)
      elems(u.id) foreach { e =>
        viz.onElement(e.id, e.ownerId, e.name, e.kind, e.unitId, e.offset)
      }
    }
  }

  /** Returns this project's complete transitive dependency set. */
  def depends :Seq[Depend] = using(_session) { dependsT.toList }

  /** Rebuilds this project's indices. */
  def reindex () = using(_session) {
    log.info(s"Rescanning $fqId...")
    _lastIndexed = System.currentTimeMillis

    // clear out our existing compunit and elements tables
    dependsT deleteWhere(d => not (d.groupId === ""))
    compunitsT deleteWhere(_.id gt 0)
    elementsT deleteWhere(_.id gt 0)

    // depends are easy, jam 'em in!
    _model.depends foreach dependsT.insert

    // process the source in the main and test directories
    processSource(_model.sourceDir, false)
    processSource(_model.testSourceDir, true)
    // TODO: write this to a file or something? or just use last mod time on database file?
  }

  /** Returns the time at which this project was last indexed. */
  def lastIndexed = {
    if (_lastIndexed == 0L) {
      val dbfile = file(_metaDir, "project.h2.db")
      if (dbfile.exists) _lastIndexed = dbfile.lastModified
    }
    _lastIndexed
  }
  private var _lastIndexed = 0L

  private def reindexIfNeeded () {
    if (_model.needsReindex(lastIndexed)) reindex()
  }

  private def findDefnLocal[T] (name :String, kinds :Set[String], f :(Project => Loc => T),
                                incTest :Boolean) = {
    // queue ourselves up for a reindex check every time we're searched
    projects invoke(_ handle(fqId) invoke(_ reindexIfNeeded()))

    // log.info(s"Seeking $name in ${this.name}")
    val mixedCase = name.toLowerCase != name
    using(_session) {
      val query = from(elementsT, compunitsT)((e, cu) => where(e.unitId === cu.id and
        (cu.isTest === false or cu.isTest === incTest) and
        (if (mixedCase) e.name === name else e.lname === name.toLowerCase))
        select(e))
      for (elem <- query ;
           if (kinds.isEmpty || kinds(elem.kind)) ;
           cu    = compunitsT where(cu => cu.id === elem.unitId) single)
      yield Loc(fqId, elem.id, elem.name, elem.kind, file(root, cu.path), elem.offset)
    } map f(this)
  }

  private def comps (loc :Loc) :List[String] = using(_session) {
    @tailrec def loop (curId :Int, comps :List[String]) :List[String] = {
      if (curId == 0) comps
      else elementsT.lookup(curId) match {
        case Some(elem) => loop(elem.ownerId, elem.name :: comps)
        case None =>
          log.warning("Missing element in comps()", "proj", fqId, "loc", loc, "curId", curId)
          comps
      }
    }
    loop(loc.elemId, Nil)
  }

  override def toString = s"[id=$id, name=$name, vers=$version, root=$rootPath]"

  private def processSource (dir :File, isTest :Boolean) {
    if (!dir.exists && !isTest) {
      log.info(s"No source for ${fqId}. Attempting download...")
      // try downloading source (TODO: in background?)
      _model.tryDownloadSource()
    }

    if (dir.exists) {
      val viz = new Visitor() {
        def onCompUnit (unitPath :String) {
          val path = unitPath.startsWith(rootPath) match {
            case true  => unitPath.substring(rootPath.size)
            case false => log.warning("Visiting compunit outside of project?", "proj", fqId,
                                      "root", rootPath, "path", unitPath) ; unitPath
          }
          lastUnitId = compunitsT.insert(CompUnit(path, isTest)).id
          elemStack = Nil
        }
        def onEnter (name :String, kind :String, offset :Int) {
          val ownerId = elemStack.headOption.getOrElse(0)
          val elem = Element(ownerId, name, name.toLowerCase, kind, lastUnitId, offset)
          elemStack = elementsT.insert(elem).id :: elemStack
        }
        def onExit (name :String) {
          elemStack = elemStack.tail
        }
        var lastUnitId = 0
        var elemStack = Nil :List[Int]
      }
      log.info(s"Extracting metadata from ${dir.getPath}...")
      Extractor.extract(dir, viz)
    }
  }

  private lazy val _model = ProjectModel.forProject(flavor, fqId, root)

  private lazy val _metaDir = {
    val dir = file(root, ".codex")
    if (!dir.exists) dir.mkdir()
    dir
  }

  // defer opening of our database until we need it; thousands of project objects are created at
  // app startup time, but not that many of them will actually get queried
  private lazy val _session = {
    val sess = DB.session(_metaDir, "project", ProjectDB, 1)
    shutdownSig onEmit { sess.close }
    sess
  }
  // TODO: revamp the above to close the session after N minutes of non-use
}

/** Provides access to a SQL database that contains project data. */
object ProjectDB extends Schema {

  /** Tracks this project's dependencies. */
  val dependsT = table[Depend]

  /** A row in the [[compunits]] table. */
  case class CompUnit (
    /** The path to this unit (relative to project root). */
    path :String,
    /** Whether this compunit is in the test or main source tree. */
    isTest :Boolean
  ) extends KeyedEntity[Int] {

    /** A unique identifier for this unit (1 or higher). */
    val id :Int = 0

    override def toString = s"$id:$path"
  }

  /** Tracks this project's compilation units. */
  val compunitsT = table[CompUnit]

  /** A row in the [[elements]] table. */
  case class Element (
    /** The element that owns this element, or zero if it's top-level. */
    ownerId :Int,
    /** The name of this element. */
    name :String,
    /** The lower-cased name of this element. */
    lname :String,
    /** The ''kind'' of this element: module, type, term, etc. */
    kind :String,
    /** The compunit in which this element is defined. */
    unitId :Int,
    /** The (character) offset in the compunit at which this element is defined. */
    offset :Int
  ) extends KeyedEntity[Int] {

    /** A unique identifier for this element (1 or higher). */
    val id :Int = 0

    override def toString = s"$name ($id, $ownerId, $kind, $unitId, $offset)"
  }

  /** Tracks this project's source elements. */
  val elementsT = table[Element]
  on(elementsT)(e => declare(
    columns(e.ownerId, e.name, e.lname, e.unitId, e.kind) are(indexed)
  ))
}
