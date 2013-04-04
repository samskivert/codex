//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import pomutil.{POM, Dependency}

import codex._
import codex.data.{Depend, FqId}

/** Provides information about a project's organization. */
abstract class ProjectModel (
  /** The directory at which this project is rooted. */
  val root :File) {

  /** The flavor identifier for this project model. */
  val flavor :String

  /** `true` if the project rooted at `root` is a valid project for our flavor. */
  def isValid :Boolean

  /** Methods to infer/extract various bits of project metadata. */
  def name :String
  def groupId :String
  def artifactId :String
  def version :String
  def sourceDir :File
  def testSourceDir :File

  /** Extracts this project's transitive dependencies. */
  def depends :Seq[Depend]

  /** Creates a file, relative to the project root, with the supplied path components. */
  protected def file (comps :String*) = ProjectModel.file(root, comps :_*)
}

object ProjectModel {

  /** Returns `Some(model)` for the project at `root` or `None` if we can't grok root. */
  def forRoot (root :File) :Option[ProjectModel] = Seq(
    new MavenProjectModel(root), new SBTProjectModel(root), new DefaultProjectModel(root)
  ) find(_.isValid)

  /** Returns the appropriate project model for a project (which has known metadata). */
  def forProject (flavor :String, fqId :FqId, root :File) = flavor match {
    case "maven" => new MavenProjectModel(root)
    case "m2"    => new M2ProjectModel(root, fqId)
    case _       => new DefaultProjectModel(root)
  }

  /** Returns `Some(model)` for a dependency, or `None` if unresolvable. */
  def forDepend (depend :Depend) = (depend.flavor match {
    case "m2" => Some(new M2ProjectModel(m2root(depend), depend.toFqId))
    case _    => None
  }) flatMap(m => if (!m.isValid) None else Some(m))

  class DefaultProjectModel (root :File) extends ProjectModel(root) {
    override val flavor = "unknown"
    override def isValid = root.isDirectory // we're not picky!
    override def name = root.getName
    override def groupId = "unknown"
    override def artifactId = root.getName
    override def version = "unknown"
    override def sourceDir = {
      val options = Seq(file("src", "main"), file("src"))
      options find(_.isDirectory) getOrElse(root)
    }
    override def testSourceDir = {
      val options = Seq(file("test"), file("src", "test"), file("tests"), file("src", "tests"))
      options find(_.isDirectory) getOrElse(root)
    }
    override def depends = Seq[Depend]() // no idea!
  }

  abstract class POMProjectModel (root :File) extends ProjectModel(root) {
    override def name =  pom.name getOrElse root.getName
    override def groupId = pom.groupId
    override def artifactId = pom.artifactId
    override def version = pom.version
    // TODO hack: add scala and java depends as appropriate
    override def depends = pom.transitiveDepends(true) map { d =>
      Depend(d.groupId, d.artifactId, d.version, "m2", d.scope == "test")
    }
    protected def pom :POM
  }

  class MavenProjectModel (root :File) extends POMProjectModel(root) {
    override val flavor = "maven"
    override def isValid = pfile.exists
    override def sourceDir = file("src", "main") // TODO: read from POM
    override def testSourceDir = file("src", "test") // TODO: read from POM

    val pfile = new File(root, "pom.xml")
    override def pom = _pom
    lazy val _pom = POM.fromFile(pfile).get
  }

  class M2ProjectModel (root :File, fqId :FqId) extends POMProjectModel(root) {
    override val flavor = "m2"
    override def isValid = pfile.exists
    // TODO: download sources if jar doesn't exist
    override def sourceDir = new File(root, pfile.getName.replaceAll(".pom", "-sources.jar"))
    override def testSourceDir = root

    val pfile = new File(root, s"${fqId.artifactId}-${fqId.version}.pom")
    override def pom = _pom
    lazy val _pom = POM.fromFile(pfile).get
  }

  class SBTProjectModel (root :File) extends DefaultProjectModel(root) {
    override val flavor = "sbt"
    // TODO there are perhaps better ways to detect SBT
    override def isValid = file("build.sbt").exists || file("projects", "Build.scala").exists
    override def name = _extracted.getOrElse("name", super.name)
    override def groupId = _extracted.getOrElse("organization", super.groupId)
    override def artifactId = _extracted.getOrElse("name", super.artifactId)
    override def version = _extracted.getOrElse("version", super.version)
    override def sourceDir = _extracted.get("compile:source-directory") map(
      new File(_)) getOrElse(super.sourceDir)
    override def testSourceDir = _extracted.get("test:source-directory") map(
      new File(_)) getOrElse(super.sourceDir)
    override def depends = _extracted.get("library-dependencies") map(SBT.parseDeps) getOrElse(Seq())

    private lazy val _extracted = SBT.extractProps(
      root, "name", "organization", "version", "library-dependencies",
      "compile:source-directory", "test:source-directory")
  }

  private def m2root (d :Depend) =
    file(m2repo, Dependency(d.groupId, d.artifactId, d.version).repositoryPath :_*)

  private def file (root :File, segs :String*) = (root /: segs)(new File(_, _))
  private val m2repo = file(new File(System.getProperty("user.home")), ".m2", "repository")
}
