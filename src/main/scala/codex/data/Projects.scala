//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{KeyedEntity, Schema}
import pomutil.POM
import samscala.nexus.Entity

import codex._

/** Provides project-related services. */
class Projects extends Entity {
  import ProjectsDB._
  import ProjectsUtil._

  /** Returns (creating if necessary and possible) the project that contains the supplied compilation
    * unit.
    */
  def forPath (path :String) :Option[Project] =
    _projects.find(p => path.startsWith(p.rootPath)) orElse tryCreateProject(path)

  /** Returns (creating if necessary and possible) the project with the specified fqId. */
  def forId (fqId :FqId) :Option[Project] =
    _projects.find(_.fqId == fqId) orElse tryCreateProject(fqId)

  private def tryCreateProject (path :String) :Option[Project] = for {
    root <- findRoot(new File(path))
    pom  <- POM.fromFile(new File(root, "pom.xml"))
  } yield {
    println("Ooh, POM " + pom)
    // TODO: insert into database, resolve depends, etc. etc.
    new Project(pom.name.getOrElse(root.getName), pom.groupId, pom.artifactId, pom.version,
                System.currentTimeMillis, root.getAbsolutePath, 0L)
  }

  private def tryCreateProject (fqId :FqId) = None // TODO

  private var _session = DB.session(codex.metaDir, "projects", ProjectsDB, 1)
  private var _projects :Seq[Project] = using(_session) { projects.toSeq }
}

/** Provides access to a SQL database that contains project data. */
object ProjectsDB extends Schema {

  /** A row in the depends table. */
  case class Depend (sourceId :Long, targetId :Long) {
    def this () = this(0L, 0L) // for unserializing
    override def toString = s"[srcId=$sourceId, tgtId=$targetId]"
  }

  val projects = table[Project]

  val depends = table[Depend]
  on(depends) { d => declare(
    d.sourceId is(indexed),
    d.targetId is(indexed)
  )}

  val dependsToProject = oneToManyRelation(projects, depends) via(_.id === _.sourceId)
  dependsToProject.foreignKeyDeclaration.constrainReference(onDelete cascade)
}

/** Provides some static utility methods. */
object ProjectsUtil {

  def findRoot (curDir :File) :Option[File] =
    if (curDir == null) None
    else if (isProjectRoot(curDir)) Some(curDir)
    else findRoot(curDir.getParentFile)

  def isProjectRoot (dir :File) = RootFiles exists(n => new File(dir, n).exists)

  // TODO: expand this and/or specialize it based on the suffix of the candidate file
  private val RootFiles = List(".git", ".hg", "build.xml", "pom.xml", "build.sbt", "Makefile")
}
