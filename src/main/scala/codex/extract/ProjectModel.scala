//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.channels.Channels
import pomutil.{POM, Dependency}

import codex._
import codex.data.{Depend, FqId, Loc}

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

  /** Extracts the paths to all projects in this project's "family". This enumerates all the modules
    * in a multi-module project by traversing up to the multimodule parent and then finding all of
    * its children, grandchildren, etc. This is only desired/implemented for local projects.
    */
  def family :Seq[File] = Seq()

  /** Attempts to download the source code for this project (only meaningful if [[isRemote]]). */
  def tryDownloadSource () {}

  /** `true` if this project appears to have documentation in the expected place. */
  def hasDocs = false

  /** Attempts to download or generate the documentation for this project. */
  def tryGenerateDocs () {}

  /** Returns the doc URL for `loc`. */
  def docUrl (loc :Loc, cs :List[String]) :String = {
    val docurl = cs flatMap(_ split("\\.")) mkString("/")
    // TODO: figure out a less hacky way of handling Scala objects
    val hackurl = loc.kind match {
      case "object" => docurl + "$"
      case _ => docurl
    }
    s"/doc/$flavor/$groupId/$artifactId/$version/$hackurl.html"
  }

  /** Returns true if this project should be reindexed.
    * @param lastIndexed the time at which the project was last indexed. */
  def needsReindex (lastIndexed :Long) =
    ((sourceDir.exists && haveNewerDir(lastIndexed)(sourceDir)) ||
      (testSourceDir.exists && haveNewerDir(lastIndexed)(testSourceDir)))

  protected def haveNewerDir(lastIndexed :Long)(dir :File) :Boolean = {
    if (dir.lastModified > lastIndexed) true
    else dir.listFiles filter(_.isDirectory) exists(haveNewerDir(lastIndexed))
  }

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
    case "sbt"   => new SBTProjectModel(root)
    case "m2"    => new M2ProjectModel(root, fqId)
    case "ivy"   => new IvyProjectModel(root, fqId)
    case _       => new DefaultProjectModel(root)
  }

  /** Returns `Some(model)` for a dependency, or `None` if unresolvable. */
  def forDepend (depend :Depend) = (depend.flavor match {
    case "m2"  => Some(new M2ProjectModel(m2root(depend), depend.toFqId))
    case "ivy" => Some(new IvyProjectModel(ivy2root(depend), depend.toFqId))
    case _     => None
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
      val options = Seq(file("src", "main"))
      options find(_.isDirectory) getOrElse(file("src"))
    }
    override def testSourceDir = {
      val options = Seq(file("src", "test"), file("tests"), file("src", "tests"))
      options find(_.isDirectory) getOrElse(file("test"))
    }
    override def depends = Seq[Depend]() // no idea!
  }

  abstract class POMProjectModel (root :File) extends ProjectModel(root) {
    override def isValid    = pfile.exists
    override def name       = _pom.name getOrElse root.getName
    override def groupId    = _pom.groupId
    override def artifactId = _pom.artifactId
    override def version    = _pom.version

    override def depends = {
      val realdeps = _pom.transitiveDepends(true) map { d =>
        Depend(d.groupId, d.artifactId, d.version, "m2", d.scope == "test")
      }
      // TEMP hack: add scala and java depends as appropriate
      val haveRealJava = realdeps.exists(d => d.groupId == "java" && d.artifactId == "jdk")
      val haveRealScala = realdeps.exists(d => d.groupId == "org.scala-lang" &&
        d.artifactId == "scala-library")
      val haveScalaSource = codex.file(sourceDir, "scala").exists
      val haveJavaSource = codex.file(sourceDir, "java").exists
      // TODO: get versions from POM
      val fakeScalaDep = if (haveScalaSource && !haveRealScala)
        Seq(Depend("org.scala-lang", "scala-library", "2.10.1", "m2", false)) else Seq()
      val fakeJavaDep = if ((haveScalaSource || haveJavaSource) && !haveRealJava)
        Seq(Depend("java", "jdk", "1.6", "m2", false)) else Seq()
      realdeps ++ fakeScalaDep ++ fakeJavaDep
    }

    protected val pfile :File
    protected lazy val _pom = POM.fromFile(pfile).get
  }

  class MavenProjectModel (root :File) extends POMProjectModel(root) {
    override val flavor        = "maven"
    override def isRemote      = false
    override def sourceDir     = _pom.buildProps.get("sourceDirectory") map(file(_)) getOrElse(
      file("src", "main"))
    override def testSourceDir = _pom.buildProps.get("testSourceDirectory") map(file(_)) getOrElse(
      file("src", "test"))

    override def family = {
      // finds our the parent-most parent that did not come from m2repo
      def eldestLocalParent (pom :POM) :POM = pom.parent match {
        case Some(parent) if (!isM2(parent.file.get)) => eldestLocalParent(parent)
        case _ => pom
      }
      // enumerates all children and grandchildren of a pom
      def allmods (pom :POM) :Seq[File] = {
        val pdir = pom.file.get.getParentFile
        def toPom (m :String) = POM.fromFile(codex.file(pdir, m, "pom.xml"))
        pdir +: (pom.allModules.distinct flatMap toPom flatMap allmods)
      }
      allmods(eldestLocalParent(_pom))
    }

    override def hasDocs = file("target", "site", "apidocs").exists
    override def tryGenerateDocs () = Maven.buildDocs(root)

    override protected val pfile = file("pom.xml")
  }

  class M2ProjectModel (root :File, fqId :FqId) extends POMProjectModel(root) {
    override val flavor        = "m2"
    override def isRemote      = true
    override def sourceDir     = artifact("sources")
    override def testSourceDir = artifact("test-sources") // no existy

    override def tryDownloadSource () = tryDownload("sources")
    override def hasDocs = artifact("javadoc").exists
    override def tryGenerateDocs () = tryDownload("javadoc")

    override def needsReindex (lastIndexed :Long) = (sourceDir.lastModified > lastIndexed)

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
        case 200 =>
          val out = new FileOutputStream(artifact(cfier)).getChannel
          try {
            val in = Channels.newChannel(uconn.getInputStream)
            try out.transferFrom(in, 0, Long.MaxValue)
            finally in.close()
          } finally out.close()
        case code => log.info(s"Download failed: $code")
      }
    }

    override protected val pfile = file(s"${fqId.artifactId}-${fqId.version}.pom")
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
    override def depends = {
      val realdeps = _extracted.get("library-dependencies") map(SBT.parseDeps) getOrElse(Seq())
      // TEMP hack: add scala and java depends as appropriate
      val haveRealJava = realdeps.exists(d => d.groupId == "java" && d.artifactId == "jdk")
      val haveRealScala = realdeps.exists(d => d.groupId == "org.scala-lang" &&
        d.artifactId == "scala-library")
      // TODO: get versions from SBT
      val fakeScalaDep = if (!haveRealScala)
        Seq(Depend("org.scala-lang", "scala-library", "2.10.1", "ivy", false)) else Seq()
      val fakeJavaDep = if (!haveRealJava) Seq(Depend("java", "jdk", "1.6", "m2", false)) else Seq()
      realdeps ++ fakeScalaDep ++ fakeJavaDep
    }

    override def hasDocs = file("target", "api").exists
    override def tryGenerateDocs () = SBT.buildDocs(root)

    // TODO: are there better ways to detect SBT?
    private lazy val _bfile = Seq(file("build.sbt"), file("projects", "Build.scala")) find(
      _.exists) getOrElse(file("build.sbt"))

    private lazy val _extracted = SBT.extractProps(
      root, "name", "organization", "version", "library-dependencies",
      "compile:source-directory", "test:source-directory")
  }

  // TODO: revamp to read data from IVY file, not fake POM file
  class IvyProjectModel (root :File, fqId :FqId) extends POMProjectModel(root) {
    override val flavor        = "ivy"
    override def isRemote      = true
    override def sourceDir     = artifact("srcs", "sources")
    override def testSourceDir = artifact("srcs", "test-sources") // no existy!

    override def tryDownloadSource () = tryDownload("srcs", "sources")
    override def hasDocs = artifact("docs", "javadoc").exists
    override def tryGenerateDocs () = tryDownload("docs", "javadoc")

    override def needsReindex (lastIndexed :Long) = (sourceDir.lastModified > lastIndexed)

    private def artifact (dir :String, cfier :String) =
      file(dir, s"${fqId.artifactId}-${fqId.version}-$cfier.jar")

    private def tryDownload (dir :String, cfier :String) {
      // TODO
      // // TODO: can we figure out what Maven repository this artifact was downloaded from?
      // val FqId(gid, aid, vers) = fqId
      // val gpath = gid.replace('.', '/')
      // val url = new URL(s"http://repo2.maven.org/maven2/$gpath/$aid/$vers/$aid-$vers-$cfier.jar")
      // log.info(s"Downloading $url...")
      // val uconn = url.openConnection.asInstanceOf[HttpURLConnection]
      // uconn.getResponseCode match {
      //   case 200 => new FileOutputStream(artifact(cfier)).getChannel transferFrom(
      //     Channels.newChannel(uconn.getInputStream), 0, Long.MaxValue)
      //   case code => log.info(s"Download failed: $code")
      // }
    }

    override protected val pfile = file("poms", s"${fqId.artifactId}-${fqId.version}.pom")
  }

  private def isM2 (file :File) = file.getPath startsWith m2repo.getPath
  private def m2root (d :Depend) = file(
    m2repo, Dependency(d.groupId, d.artifactId, d.version).repositoryPath :_*)
  private val m2repo = file(home, ".m2", "repository")

  private def ivy2root (d :Depend) = file(ivy2repo, "cache", d.groupId, d.artifactId)
  private val ivy2repo = file(home, ".ivy2")
}
