//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema}
import samscala.nexus.Entity

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
  /** When this project was imported into the library. */
  val imported :Long,
  /** The directory at which this project is rooted, as a string. */
  val rootPath :String
) extends KeyedEntity[Long] with Entity {
  import ProjectDB._

  // def this () = this("", "", "", "", 0L, "") // for unserializing

  /** A unique identifier for this project (1 or higher). */
  val id :Long = 0L

  /** The directory at which this project is rooted. */
  lazy val root = new File(rootPath)

  /** This project's fully qualified (aka Maven) id, or some approximation thereof. */
  lazy val fqId = FqId(groupId, artifactId, version)

  /** Returns all definitions in this project with the specified name. */
  def findDefn (name :String) :Iterable[Loc] = {
    log.info("TODO: findDefn " + this.name + " " + name)

    // if we've never been updated, do a scan now before we return results
    if (lastUpdated == 0L) rescanProject()
    else if (needsUpdate) projects invoke (_ handle(fqId) invoke (_ rescanProject()))

    // search this project and its transitive depends
    findDefnLocal(name) ++ using(_session) {
      for (dep <- depends where(_.forTest === false) map(_.toFqId) ;
           deph <- projects request(_.forId(dep)) toSeq ;
           loc <- deph request(_.findDefnLocal(name))) yield loc
    }
    // (TODO: handle forTest, return results incrementally?)
  }

  private def findDefnLocal (name :String) = using(_session) {
    for (elem <- elements where(e => e.name === name) ;
         cu    = compunits where(cu => cu.id === elem.unitId) single)
    yield Loc(fqId, elem.name, new File(root, cu.path), elem.offset)
  }

  override def toString = s"[id=$id, name=$name, vers=$version, root=$rootPath]"

  private def rescanProject () = using(_session) {
    log.info(s"Rescanning $fqId")
    // clear out our existing compunit and elements tables
    depends deleteWhere(d => not (d.groupId === ""))
    compunits deleteWhere(_.id gt 0)
    elements deleteWhere(_.id gt 0)

    val model = ProjectModel.infer(fqId, root)

    def toDep (forTest :Boolean)(fqId :FqId) =
      Depend(fqId.groupId, fqId.artifactId, fqId.version, forTest)
    val (deps, testDeps) = model.depends
    deps map(toDep(false)) foreach depends.insert
    testDeps map(toDep(true)) foreach depends.insert

    val sourceDir = model.sourceDir
    if (sourceDir.exists) {
      val viz = new Visitor() {
        def onCompUnit (unitPath :String) {
          val path = if (unitPath.startsWith(rootPath)) unitPath.substring(rootPath.size)
          else {
            log.warning("Visiting compunit outside of project?", "proj", fqId, "root", rootPath,
                        "path", unitPath)
            unitPath
          }
          lastUnitId = compunits.insert(CompUnit(path)).id
        }
        def onEnter (name :String, kind :String, offset :Int) {
          val ownerId = elemStack.headOption.getOrElse(0)
          val elem = Element(ownerId, name, kind, lastUnitId, offset)
          elemStack = elements.insert(elem).id :: elemStack
        }
        def onExit (name :String) {
          elemStack = elemStack.tail
        }
        var lastUnitId = 0
        var elemStack = Nil :List[Int]
      }
      Extractor.extract(sourceDir, viz)
      // TODO: extract elements from test source (mark them as such)

    } else {
      log.info(s"No source for ${fqId}. Skipping...")
      // TODO: try downloading source from Maven Central (in background?)
    }

    _lastUpdated = System.currentTimeMillis
  }

  private def needsUpdate = false // TODO

  private def lastUpdated = {
    if (_lastUpdated == 0L) {
    }
    _lastUpdated
  }
  private var _lastUpdated = 0L

  private lazy val metaDir = {
    val dir = new File(rootPath, ".codex")
    if (!dir.exists) dir.mkdir()
    dir
  }

  // defer opening of our database until we need it; thousands of project objects are created at
  // app startup time, but not that many of them will actually get queried
  private lazy val _session = {
    val sess = DB.session(metaDir, "project", ProjectDB, 1)
    shutdownSig onEmit { sess.close }
    sess
  }
  // TODO: revamp the above to close the session after N minutes of non-use
}

/** Provides access to a SQL database that contains project data. */
object ProjectDB extends Schema {

  /** A row in the [[depends]] table. */
  case class Depend (
    /** The groupId of the depended upon project. */
    groupId :String,
    /** The artifactId of the depended upon project. */
    artifactId :String,
    /** The version of the depended upon project. */
    version :String,
    /** Whether this is a normal or testing-only dependency. */
    forTest :Boolean
  ) {
    def this () = this("", "", "", false) // for unserializing

    def toFqId = FqId(groupId, artifactId, version)
  }

  /** Tracks this project's dependencies. */
  val depends = table[Depend]

  /** A row in the [[compunits]] table. */
  case class CompUnit (
    /** The path to this unit (relative to project root). */
    path :String
  ) extends KeyedEntity[Int] {
    def this () = this("") // for unserializing

    /** A unique identifier for this unit (1 or higher). */
    val id :Int = 0
  }

  /** Tracks this project's compilation units. */
  val compunits = table[CompUnit]

  /** A row in the [[elements]] table. */
  case class Element (
    /** The element that owns this element, or zero if it's top-level. */
    ownerId :Int,
    /** The name of this element. */
    name :String,
    /** The ''kind'' of this element: module, type, term, etc. */
    kind :String,
    /** The compunit in which this element is defined. */
    unitId :Int,
    /** The (character) offset in the compunit at which this element is defined. */
    offset :Int
  ) extends KeyedEntity[Int] {
    def this () = this(0, "", "", 0, 0) // for unserializing

    /** A unique identifier for this element (1 or higher). */
    val id :Int = 0
  }

  /** Tracks this project's source elements. */
  val elements = table[Element]
  on(elements)(e => declare(
    columns(e.ownerId, e.name, e.unitId, e.kind) are(indexed)
  ))
}
