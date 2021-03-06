//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema}
import samscala.nexus.{Entity, Handle, entity}
import scala.collection.mutable.{Map => MMap}

import codex._
import codex.extract.ProjectModel

/** Provides project-related services. */
class Projects extends Entity {
  import ProjectsDB._
  import ProjectsUtil._

  /** Returns (creating if necessary) the project that contains the supplied comp unit. */
  def forPath (path :String) :Option[Handle[Project]] = bestPath(path) orElse (
    findRoot(new File(path)) flatMap ProjectModel.forRoot map createProject)

  /** Returns (creating if necessary) the project at the specified root. */
  def forRoot (root :File) :Option[Handle[Project]] =
    (_byPath get root.getPath) orElse (ProjectModel.forRoot(root) map createProject)

  /** Returns (creating if necessary) the project for the specified dependency. */
  def forDep (depend :Depend) :Option[Handle[Project]] =
    (_byFqId get depend.toFqId) orElse (ProjectModel.forDepend(depend) map createProject)

  /** Returns the project for the supplied fqId or `None` if no such project is known. */
  def forId (fqId :FqId) :Option[Handle[Project]] = _byFqId get fqId

  /** Returns the project for the supplied id or `None` if no such project is known. */
  def forId (id :Long) :Option[Handle[Project]] = _byId get id

  /** Returns (id, fqId, rootPath) for all known projects. */
  def ids :Iterable[(Long, FqId,String)] = using(_session) {
    projects.allRows map(p => (p.id, p.fqId, p.rootPath))
  }

  /** Updates the version number of the project with the specified id. This unmaps the project with
    * its current version, reresolves it with its new version and forces a reindex. */
  def reversion (p :Project, newVers :String) {
    _byId remove(p.id) match {
      case None => log.warning("Can't reversion unknown project", "id", p.id, "newVers", newVers)
      case Some(ph) =>
        _byFqId -= p.fqId
        _byPath -= p.rootPath
        using(_session) {
          // update the project's version in the projects table
          update(projects)(pj => where(pj.id === p.id) set(pj.version := newVers))
          // now reload the project record, remap it, and trigger a reindex
          projects lookup(p.id)
        } map(map) foreach(_ invoke(_.reindex()))
        // close down the old resolved version of the project
        ph invoke(_.close())
    }
  }

  /** Deletes the project with the specified `fqId`. */
  def delete (id :Long) {
    _byId remove(id) match {
      case None => log.warning("Can't delete unknown project", "id", id)
      case Some(ph) =>
        val (fqId, path) = ph request(p => (p.fqId, p.rootPath))
        _byFqId -= fqId
        _byPath -= path
        using(_session) { projects delete(id) }
        ph invoke(_.delete())
    }
  }

  // a backdoor to give projects easy access to their entity handle
  private[data] def handle (id :Long) = _byId(id)

  private def bestPath (path :String) = (("", None :Option[Handle[Project]]) /: _byPath) {
    case ((broot, bp), (root, p)) =>
      if (path.startsWith(root + File.separator) && (root.length > broot.length)) (root, Some(p))
      else (broot, bp)
  } _2

  private def createProject (pm :ProjectModel) = using(_session) {
    log.info("Creating project", "flavor", pm.flavor, "path", pm.root)

    // if we already have a project with this fqId, we either need to usurp it, or take a back seat
    // to it; in the former case, we mutate the existing project's version and take the "stock"
    // version for ourselves, in the latter case we just register ourselves with a mutated version
    val stockFqId = FqId(pm.groupId, pm.artifactId, pm.version)
    val fqId = _byFqId get(stockFqId) match {
      case None => stockFqId
      case Some(ph) =>
        // if we're local and the other project is remote; delete the other project
        val (otherId, otherRemote, otherRoot) = ph request(p => (p.id, p.isRemote, p.rootPath))
        if (otherRemote && !pm.isRemote) {
          log.info(s"Usurping remote $stockFqId with local", "usurper", pm.root)
          delete(otherId)
          stockFqId
        }
        // else we're both local; if the other project's root no longer exists; usurp it
        else if (!new File(otherRoot).exists) {
          log.info(s"Usurping obsolete local $stockFqId", "obsolete", otherRoot, "usurper", pm.root)
          delete(otherId)
          stockFqId
        }
        // otherwise we're both valid local projects; tack a -N suffix onto our version
        else {
          var suff = 1
          var newFqId = stockFqId dedupe(suff)
          while (_byFqId contains(newFqId)) {
            suff += 1
            newFqId = stockFqId dedupe(suff)
          }
          log.info(s"Assigned $newFqId to conflicting project",
                   "have", otherRoot, "conflicter", pm.root)
          newFqId
        }
        // currently, it's not possible for us both to be remote, because we always find an
        // existing remote depend when looking for a depend by fqId (the only way new remote
        // depends are ever created)
    }
    val p = projects insert new Project(
      name          = pm.name,
      groupId       = fqId.groupId,
      artifactId    = fqId.artifactId,
      version       = fqId.version,
      flavor        = pm.flavor,
      imported      = System.currentTimeMillis,
      rootPath      = pm.root.getAbsolutePath)
    log.info("Created project", "fqid", p.fqId, "root", pm.root)
    map(p)
  }

  private def map (p :Project) = {
    val pe = entity(p)
    _byPath += (p.rootPath -> pe)
    _byId += (p.id -> pe)
    _byFqId += (p.fqId -> pe)
    pe
  }

  private val _byId = MMap[Long,Handle[Project]]()
  private val _byPath = MMap[String,Handle[Project]]()
  private val _byFqId = MMap[FqId,Handle[Project]]()
  private val _session = DB.session(codex.metaDir, "projects", ProjectsDB, 1)

  // map all of our existing known projects
  using(_session) { projects.allRows foreach map }
  // close our session on shutdown
  shutdownSig.onEmit { _session.close }
}

/** Provides access to a SQL database that contains projects data. */
object ProjectsDB extends Schema {

  /** Contains metadata for all known projects. */
  val projects = table[Project]
}

/** Provides some static utility methods. */
object ProjectsUtil {

  def findRoot (curDir :File) :Option[File] =
    if (curDir == null) None
    else if (!curDir.isDirectory) findRoot(curDir.getParentFile)
    else curDir.listFiles collectFirst {
      case f if (RootFiles(f.getName)) => curDir
      case f if (f.getName.endsWith(".csproj")) => f
    } orElse findRoot(curDir.getParentFile)

  // TODO: expand this and/or specialize it based on the suffix of the candidate file
  private val RootFiles = Set(".git", ".hg", "build.xml", "pom.xml", "build.sbt", "Makefile",
                              ".classpath", ".project")
}
