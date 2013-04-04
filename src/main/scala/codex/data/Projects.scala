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
  def forPath (path :String) :Option[Handle[Project]] = _byPath collectFirst {
    case (root, p) if (path startsWith root) => p
  } orElse (findRoot(new File(path)) flatMap ProjectModel.forRoot map createProject)

  /** Returns (creating if necessary) the project for the specified dependency. */
  def forDep (depend :Depend) :Option[Handle[Project]] =
    (_byFqId get depend.toFqId) orElse (ProjectModel.forDepend(depend) map createProject)

  /** Returns the project for the supplied fqId or `None` if no such project is known. */
  def forId (fqId :FqId) :Option[Handle[Project]] = _byFqId get fqId

  /** Returns (fqId, rootPath) for all known projects. */
  def ids :Iterable[(FqId,String)] = using(_session) {
    projects map(p => (p.fqId, p.rootPath))
  }

  // a backdoor to give projects easy access to their entity handle
  private[data] def handle (fqId :FqId) = _byFqId(fqId)

  private def createProject (pm :ProjectModel) = using(_session) {
    val p = projects insert new Project(
      name          = pm.name,
      groupId       = pm.groupId,
      artifactId    = pm.artifactId,
      version       = pm.version,
      flavor        = pm.flavor,
      imported      = System.currentTimeMillis,
      rootPath      = pm.root.getAbsolutePath)
    log.info("Created project", "fqid", p.fqId, "root", pm.root)
    map(p)
  }

  private def map (p :Project) = {
    val pe = entity(p)
    _byPath += (p.rootPath -> pe)
    _byFqId += (p.fqId -> pe)
    pe
  }

  private val _byPath = MMap[String,Handle[Project]]()
  private val _byFqId = MMap[FqId,Handle[Project]]()
  private val _session = DB.session(codex.metaDir, "projects", ProjectsDB, 1)

  // map all of our existing known projects
  using(_session) { projects foreach map }
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
    else if (isProjectRoot(curDir)) Some(curDir)
    else findRoot(curDir.getParentFile)

  def isProjectRoot (dir :File) = RootFiles exists (n => new File(dir, n).exists)

  // TODO: expand this and/or specialize it based on the suffix of the candidate file
  private val RootFiles = List(".git", ".hg", "build.xml", "pom.xml", "build.sbt", "Makefile")
}
