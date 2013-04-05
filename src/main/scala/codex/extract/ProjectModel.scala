//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.channels.Channels
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

  /** `true` if this is a remote project (e.g. its data comes from .m2 or .ivy or similar).
    * `false` if this is a local project (we have the source checked out locally). */
  def isRemote :Boolean

  /** Methods to infer/extract various bits of project metadata. */
  def name :String
  def groupId :String
  def artifactId :String
  def version :String
  def sourceDir :File
  def testSourceDir :File

  /** Extracts this project's transitive dependencies. */
  def depends :Seq[Depend]

  /** Attempts to download the source code for this project (only meaningful if [[isRemote]]). */
  def tryDownloadSource () {}

  /** `true` if this project appears to have documentation in the expected place. */
  def hasDocs = false

  /** Attempts to download or generate the documentation for this project. */
  def tryGenerateDocs () {}

  /** Returns true if this project should be reindexed.
    * @param lastIndexed the time at which the project was last indexed. */
  def needsReindex (lastIndexed :Long) =
    // default is not super smart but is a good basis for non-local projects
    (sourceDir.lastModified > lastIndexed || testSourceDir.lastModified > lastIndexed)

  /** Creates a file, relative to the project root, with the supplied path components. */
  protected def file (comps :String*) = codex.file(root, comps :_*)
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
    override val flavor     = "unknown"
    override def isValid    = root.isDirectory // we're not picky!
    override def isRemote   = false
    override def name       = root.getName
    override def groupId    = "unknown"
    override def artifactId = root.getName
    override def version    = "unknown"

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
    override def name       = pom.name getOrElse root.getName
    override def groupId    = pom.groupId
    override def artifactId = pom.artifactId
    override def version    = pom.version

    // TODO hack: add scala and java depends as appropriate
    override def depends = pom.transitiveDepends(true) map { d =>
      Depend(d.groupId, d.artifactId, d.version, "m2", d.scope == "test")
    }

    protected def pom :POM
  }

  class MavenProjectModel (root :File) extends POMProjectModel(root) {
    override val flavor        = "maven"
    override def isValid       = pfile.exists
    override def isRemote      = false
    override def sourceDir     = pom.buildProps.get("sourceDirectory") map(file(_)) getOrElse(
      file("src", "main"))
    override def testSourceDir = pom.buildProps.get("testSourceDirectory") map(file(_)) getOrElse(
      file("src", "test"))

    override def hasDocs = file("target", "site", "apidocs").exists
    override def tryGenerateDocs () = Maven.buildDocs(root)

    val pfile = file("pom.xml")
    override def pom = _pom
    lazy val _pom = POM.fromFile(pfile).get

    override def needsReindex (lastIndexed :Long) =
      // TODO: should we check every subdirectory of {testS|s}ourceDir? probably...
      super.needsReindex(lastIndexed) || (pfile.lastModified > lastIndexed)
  }

  class M2ProjectModel (root :File, fqId :FqId) extends POMProjectModel(root) {
    override val flavor        = "m2"
    override def isValid       = pfile.exists
    override def isRemote      = true
    override def sourceDir     = artifact("sources")
    override def testSourceDir = root

    override def tryDownloadSource () = tryDownload("sources")
    override def hasDocs = artifact("javadoc").exists
    override def tryGenerateDocs () = tryDownload("javadoc")

    override def needsReindex (lastIndexed :Long) =
      super.needsReindex(lastIndexed) || (pfile.lastModified > lastIndexed)

    private def artifact (cfier :String) =
      file(pfile.getName.replaceAll(".pom", s"-$cfier.jar"))

    private def tryDownload (cfier :String) {
      // TODO: can we figure out what Maven repository this artifact was downloaded from?
      val FqId(gid, aid, vers) = fqId
      val gpath = gid.replace('.', '/')
      val url = new URL(s"http://repo2.maven.org/maven2/$gpath/$aid/$vers/$aid-$vers-$cfier.jar")
      log.info(s"Downloading $url...")
      val uconn = url.openConnection.asInstanceOf[HttpURLConnection]
      uconn.getResponseCode match {
        case 200 => new FileOutputStream(artifact(cfier)).getChannel transferFrom(
          Channels.newChannel(uconn.getInputStream), 0, Long.MaxValue)
        case code => log.info(s"Download failed: $code")
      }
    }

    val pfile = file(s"${fqId.artifactId}-${fqId.version}.pom")
    override def pom = _pom
    lazy val _pom = POM.fromFile(pfile).get
  }

  class SBTProjectModel (root :File) extends DefaultProjectModel(root) {
    override val flavor     = "sbt"
    override def isValid    = _bfile.exists
    override def isRemote   = false
    override def name       = _extracted.getOrElse("name", super.name)
    override def groupId    = _extracted.getOrElse("organization", super.groupId)
    override def artifactId = _extracted.getOrElse("name", super.artifactId)
    override def version    = _extracted.getOrElse("version", super.version)

    override def sourceDir = _extracted.get("compile:source-directory") map(
      new File(_)) getOrElse(super.sourceDir)
    override def testSourceDir = _extracted.get("test:source-directory") map(
      new File(_)) getOrElse(super.sourceDir)
    override def depends = _extracted.get("library-dependencies") map(SBT.parseDeps) getOrElse(Seq())

    override def hasDocs = file("target", "api").exists
    override def tryGenerateDocs () = SBT.buildDocs(root)

    override def needsReindex (lastIndexed :Long) =
      super.needsReindex(lastIndexed) || (_bfile.lastModified > lastIndexed)

    // TODO: are there better ways to detect SBT?
    private lazy val _bfile = Seq(file("build.sbt"), file("projects", "Build.scala")) find(
      _.exists) getOrElse(file("build.sbt"))

    private lazy val _extracted = SBT.extractProps(
      root, "name", "organization", "version", "library-dependencies",
      "compile:source-directory", "test:source-directory")
  }

  private def m2root (d :Depend) =
    file(m2repo, Dependency(d.groupId, d.artifactId, d.version).repositoryPath :_*)

  private val m2repo = file(home, ".m2", "repository")
}
