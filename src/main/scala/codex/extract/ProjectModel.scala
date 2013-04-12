//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.channels.Channels
import pomutil.{POM, Dependency}
import scala.collection.mutable.{Set => MSet}

import codex._
import codex.data.{Depend, FqId, Loc, Project}

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

  /** Extracts this project's transitive dependencies. */
  def depends :Seq[Depend]

  /** Extracts the paths to all projects in this project's "family". This enumerates all the modules
    * in a multi-module project by traversing up to the multimodule parent and then finding all of
    * its children, grandchildren, etc. This is only desired/implemented for local projects.
    */
  def family :Seq[File] = Seq()

  /** Applies `f` to all source files in this project. The first argument indicates whether the file
    * (second arg) is test source (true) or main source (false). */
  def applyToSource (f :Boolean => File => Unit)

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
  def needsReindex (lastIndexed :Long) = sourceExists(_.lastModified > lastIndexed)

  /** Returns true if a source file exists that satisfies `p`. */
  protected def sourceExists (p :File => Boolean) :Boolean

  /** Creates a file, relative to the project root, with the supplied path components. */
  protected def file (comps :String*) = codex.file(root, comps :_*)
}

object ProjectModel {

  /** Returns `Some(model)` for the project at `root` or `None` if we can't grok root. */
  def forRoot (root :File) :Option[ProjectModel] = Seq(
    new MavenProjectModel(root),
    new SBTProjectModel(root),
    new CSProjectModel(root),
    new DefaultProjectModel(root)
  ) find(_.isValid)

  /** Returns the appropriate project model for a project (which has known metadata). */
  def forProject (proj :Project) = proj.flavor match {
    case "maven"  => new MavenProjectModel(proj.root)
    case "sbt"    => new SBTProjectModel(proj.root)
    case "m2"     => new M2ProjectModel(proj.root, proj.fqId)
    case "ivy"    => new IvyProjectModel(proj.root, proj.fqId)
    case "cs"     => new CSProjectModel(file(proj.root, proj.name + ".csproj")) // meh, hack
    case "mtcore" => new MTCoreProjectModel
    case _        => new DefaultProjectModel(proj.root)
  }

  /** Returns `Some(model)` for a dependency, or `None` if unresolvable. */
  def forDepend (depend :Depend) = (depend.flavor match {
    case "m2"     => Some(new M2ProjectModel(m2root(depend), depend.toFqId))
    case "ivy"    => Some(new IvyProjectModel(ivy2root(depend), depend.toFqId))
    case "mtcore" => Some(new MTCoreProjectModel)
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
    override def depends    = Seq[Depend]() // no idea!

    override def applyToSource (f :Boolean => File => Unit) {
      srcDir foreach applyAll(f(false))
      testSrcDir foreach applyAll(f(true))
    }

    protected def sourceExists (p :File => Boolean) =
      (srcDir map(exists(p)) getOrElse false) || (testSrcDir map(exists(p)) getOrElse false)

    protected def srcDir = firstDir(file("src", "main"), file("src"))
    protected def testSrcDir = firstDir(file("src", "test"), file("test"),
                                        file("src", "tests"), file("tests"))
  }

  abstract class POMProjectModel (root :File, val pfile :File) extends ProjectModel(root) {
    override def isValid    = pfile.exists
    override def name       = _pom.name getOrElse root.getName
    override def groupId    = _pom.groupId
    override def artifactId = _pom.artifactId
    override def version    = _pom.version

    override def depends = _pom.transitiveDepends(true) map { d =>
      Depend(d.groupId, d.artifactId, d.version, "m2", d.scope == "test")
    }

    protected lazy val _pom = POM.fromFile(pfile).get
  }

  class MavenProjectModel (root :File) extends POMProjectModel(root, file(root, "pom.xml")) {
    override val flavor   = "maven"
    override def isRemote = false

    override def depends = {
      val realdeps = super.depends
      // TEMP hack: add scala and java depends as appropriate
      val haveRealJava = realdeps.exists(d => d.groupId == "java" && d.artifactId == "jdk")
      val haveRealScala = realdeps.exists(d => d.groupId == "org.scala-lang" &&
        d.artifactId == "scala-library")
      val haveScalaSource = codex.file(_srcDir, "scala").exists
      val haveJavaSource = codex.file(_srcDir, "java").exists
      // TODO: get versions from POM
      val fakeScalaDep = if (haveScalaSource && !haveRealScala)
        Seq(Depend("org.scala-lang", "scala-library", "2.10.1", "m2", false)) else Seq()
      val fakeJavaDep = if ((haveScalaSource || haveJavaSource) && !haveRealJava)
        Seq(Depend("java", "jdk", "1.6", "m2", false)) else Seq()
      realdeps ++ fakeScalaDep ++ fakeJavaDep
    }

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

    override def applyToSource (f :Boolean => File => Unit) {
      applyAll(f(false))(_srcDir)
      applyAll(f(true))(_testSrcDir)
    }

    override def hasDocs = file("target", "site", "apidocs").exists
    override def tryGenerateDocs () = Maven.buildDocs(root)

    override protected def sourceExists (p :File => Boolean) =
      exists(p)(_srcDir) || exists(p)(_testSrcDir)

    private def srcDir (name :String, defdir :String) =
      _pom.buildProps.get(name) map(file(_)) getOrElse(file("src", defdir))
    private lazy val _srcDir = srcDir("sourceDirectory", "main")
    private lazy val _testSrcDir = srcDir("testSourceDirectory", "test")
  }

  class M2ProjectModel (root :File, fqId :FqId) extends POMProjectModel(
    root, file(root, s"${fqId.artifactId}-${fqId.version}.pom")) {
    override val flavor        = "m2"
    override def isRemote      = true

    override def applyToSource (f :Boolean => File => Unit) {
      val src = artifact("sources")
      if (!src.exists) tryDownload("sources")
      if (src.exists) f(false)(src)
    }

    override def hasDocs = artifact("javadoc").exists
    override def tryGenerateDocs () = tryDownload("javadoc")

    override protected def sourceExists (p :File => Boolean) = p(artifact("sources"))

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
  }

  class SBTProjectModel (root :File) extends DefaultProjectModel(root) {
    override val flavor     = "sbt"
    override def isValid    = _bfile.exists
    override def isRemote   = false
    override def name       = _extracted.getOrElse("name", super.name)
    override def groupId    = _extracted.getOrElse("organization", super.groupId)
    override def artifactId = _extracted.getOrElse("name", super.artifactId)
    override def version    = _extracted.getOrElse("version", super.version)

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

    override protected def srcDir =
      (_extracted.get("compile:source-directory") map(new File(_))) orElse super.srcDir
    override protected def testSrcDir =
      (_extracted.get("test:source-directory") map(new File(_))) orElse super.testSrcDir

    // TODO: are there better ways to detect SBT?
    private lazy val _bfile = Seq(file("build.sbt"), file("projects", "Build.scala")) find(
      _.exists) getOrElse(file("build.sbt"))

    private lazy val _extracted = SBT.extractProps(
      root, "name", "organization", "version", "library-dependencies",
      "compile:source-directory", "test:source-directory")
  }

  // TODO: revamp to read data from IVY file, not fake POM file
  class IvyProjectModel (root :File, fqId :FqId) extends POMProjectModel(
    root, file(root, "poms", s"${fqId.artifactId}-${fqId.version}.pom")) {
    override val flavor        = "ivy"
    override def isRemote      = true

    override def applyToSource (f :Boolean => File => Unit) {
      val src = artifact("srcs", "sources")
      if (!src.exists) tryDownload("srcs", "sources")
      if (src.exists) f(false)(src)
    }

    override def hasDocs = artifact("docs", "javadoc").exists
    override def tryGenerateDocs () = tryDownload("docs", "javadoc")

    override protected def sourceExists (p :File => Boolean) = p(artifact("srcs", "sources"))

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
  }

  class MTCoreProjectModel extends ProjectModel(file("/Developer/MonoTouch/Source")) {
    override val flavor     = mtCoreDep.flavor
    override def isValid    = root.isDirectory
    override def isRemote   = false
    override def name       = "MonoTouch Core"
    override def groupId    = mtCoreDep.groupId
    override def artifactId = mtCoreDep.artifactId
    override def version    = mtCoreDep.version
    override def depends    = Seq[Depend]() // none

    override def applyToSource (f :Boolean => File => Unit) {
      applyAll(f(false))(_srcDir)
    }

    override def needsReindex (lastIndexed :Long) = _srcDir.lastModified > lastIndexed
    override protected def sourceExists (p :File => Boolean) = error("not used")

    // TODO: special doc URL

    lazy val _srcDir = file("mono", "mcs", "class")
  }

  // our root is the .csproj file rather than its containing directory
  class CSProjectModel (csproj :File) extends DefaultProjectModel(csproj.getParentFile) {
    override val flavor     = "cs"
    override def isValid    = csproj.exists
    override def isRemote   = false
    override def name       = csproj.getName dropRight ".csproj".length
    override def groupId    = _info.rootNamespace
    override def artifactId = _info.assemblyName
    override def version    = _info.version

    override def depends = {
      val sysdeps = if (_info.refs.exists(_.name == "monotouch"))
        Seq(mtCoreDep) // TODO: also monotouch
      else if (_info.refs.exists(_.name == "MonoMac")) Seq() // TODO: monomac deps?
      else Seq() // TODO: mscorelib deps
      sysdeps // TODO: actual project deps?
    }

    override def applyToSource (f :Boolean => File => Unit) {
      _info.sources foreach f(false) // TODO: how to differentiate test sources?
    }

    override protected def sourceExists (p :File => Boolean) = _info.sources exists p

    // override def hasDocs = file("target", "api").exists
    // override def tryGenerateDocs () = SBT.buildDocs(root)

    private lazy val _info = CSProj.parse(csproj)
  }

  private def firstDir (dirs :File*) = dirs find(_.isDirectory)

  private def applyAll (f :File => Unit)(root :File) {
    val seen = MSet[File]()
    def loop (dir :File) {
      dir.listFiles map(_.getCanonicalFile) filterNot(seen) foreach { file =>
        seen += file
        if (file.isFile) f(file)
        else if (file.isDirectory && !isSkipDir(file)) loop(file)
      }
    }
    if (root.isDirectory) loop(root)
  }

  private def exists (p :File => Boolean)(root :File) = {
    val seen = MSet[File]()
    def loop (dir :File) :Boolean = {
      dir.listFiles filterNot(seen) exists { file =>
        seen += file
        (file.isFile && p(file)) || (file.isDirectory && !isSkipDir(file) && loop(file))
      }
    }
    root.isDirectory && loop(root)
  }

  private[extract] val mtCoreDep = Depend("com.xamarin", "monotouch-core", "0", "mtcore", false)

  // TODO: allow further customization
  private def isSkipDir (dir :File) = SkipDirNames(dir.getName)
  private val SkipDirNames = Set(".", "..", "CVS", ".svn", ".git", ".hg")

  private def isM2 (file :File) = file.getPath startsWith m2repo.getPath
  private def m2root (d :Depend) = file(
    m2repo, Dependency(d.groupId, d.artifactId, d.version).repositoryPath :_*)
  private val m2repo = file(home, ".m2", "repository")

  private def ivy2root (d :Depend) = file(ivy2repo, "cache", d.groupId, d.artifactId)
  private val ivy2repo = file(home, ".ivy2")
}
