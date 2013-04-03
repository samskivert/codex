//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema}
import pomutil.POM
import samscala.nexus.{Entity, Handle, entity}
import scala.collection.mutable.{Map => MMap}

import codex._

/** Provides project-related services. */
class Projects extends Entity {
  import ProjectsDB._
  import ProjectsUtil._

  /** Returns (creating if necessary) the project that contains the supplied comp unit. */
  def forPath (path :String) :Option[Handle[Project]] = _byPath collectFirst {
    case (root, p) if (path startsWith root) => p
  } orElse tryCreateProject(path)

  /** Returns (creating if necessary) the project with the specified fqId. */
  def forId (fqId :FqId) :Option[Handle[Project]] = _byFqId get fqId orElse tryCreateProject(fqId)

  // a backdoor to give projects easy access to their entity handle
  private[data] def handle (fqId :FqId) = _byFqId(fqId)

  private def tryCreateProject (path :String) :Option[Handle[Project]] = for {
    root <- findRoot(new File(path))
    pom  <- POM.fromFile(new File(root, "pom.xml"))
  } yield createProject(root, pom)

  private def tryCreateProject (fqId :FqId) = {
    // TODO: support other file types?
    val home = new File(System.getProperty("user.home"))
    val m2path = Seq(".m2", "repository") ++ fqId.groupId.split("\\.") ++ Seq(
      fqId.artifactId, fqId.version, s"${fqId.artifactId}-${fqId.version}.pom")
    val m2file = (home /: m2path)(new File(_, _))
    if (m2file.exists) POM.fromFile(m2file) map(createProject(m2file.getParentFile, _))
    else None
  }

  private def createProject (root :File, pom :POM) = using(_session) {
    val p = projects insert new Project(
      name          = pom.name getOrElse root.getName,
      groupId       = pom.groupId,
      artifactId    = pom.artifactId,
      version       = pom.version,
      imported      = System.currentTimeMillis,
      rootPath      = root.getAbsolutePath)
    log.info("Created project", "fqid", p.fqId, "root", root)
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
