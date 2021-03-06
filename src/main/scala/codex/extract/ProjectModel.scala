//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import java.net.URL
import pomutil.{POM, Dependency}
import scala.collection.mutable.{Set => MSet}

import codex._
import codex.data.{Depend, FqId, Loc, Project}

/** Provides information about a project's organization. */
abstract class ProjectModel (
  /** The root for this project. Usually a directory, but not always. */
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
  def docUrl (loc :Loc) :String = {
    val docurl = loc.path flatMap(_ split("\\.")) mkString("/")
    // TODO: figure out a less hacky way of handling Scala objects
    val hackurl = loc.kind match {
      case "object" => docurl + "$"
      case _ => docurl
    }
    s"/doc/$flavor/$groupId/$artifactId/$version/$hackurl.html"
  }

  /** Returns true if this project model should be discarded and rebuilt. Generally this means the
    * project metadata file has changed. */
  def needsReload (lastLoaded :Long) = false

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
    // local flavors
    case "maven"  => new MavenProjectModel(proj.root)
    case "sbt"    => new SBTProjectModel(proj.root)
    case "cs"     => new CSProjectModel(file(proj.root, proj.name + ".csproj")) // meh, hack
    // remote flavors
    case "m2"     => new M2ProjectModel(proj.root, proj.fqId)
    case "ivy"    => new IvyProjectModel(proj.root, proj.fqId)
    case "dll"    => new DllProjectModel(proj.root)
    // "special" flavors
    case "mtcore" => new MTCoreProjectModel
    case "jdk"    => new JavaHomeProjectModel(new JVMs.JVM(proj.root))
    case _        => new DefaultProjectModel(proj.root)
  }

  /** Returns `Some(model)` for a dependency, or `None` if unresolvable. */
  def forDepend (dep :Depend) = (dep.flavor match {
    case "m2"     => Some(new M2ProjectModel(m2root(dep), dep.toFqId))
    case "ivy"    => Some(new IvyProjectModel(ivy2root(dep), dep.toFqId))
    case "mtcore" => Some(new MTCoreProjectModel)
    case "jdk"    => JVMs.findBestMatch(dep.version) map(new JavaHomeProjectModel(_))
    case "dll"    => Dll.find(dep) map(new DllProjectModel(_))
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

    protected def srcDir = firstDir(file("src", "main"), file("src"), file("."))
    protected def testSrcDir = firstDir(file("src", "test"), file("test"),
                                        file("src", "tests"), file("tests"))
  }

  class JavaHomeProjectModel (jvm :JVMs.JVM) extends ProjectModel(jvm.root) {
    override val flavor     = "jdk"
    override def isValid    = root.isDirectory
    override def isRemote   = true
    override def name       = "jdk"
    override def groupId    = "java"
    override def artifactId = "jdk"
    override def version    = jvm.version
    override def depends    = Seq()
    override def hasDocs    = true

    override def applyToSource (f :Boolean => File => Unit) {
      f(false)(file("src.zip"))
      if (jvm.platformVers.toInt >= 8) f(false)(file("javafx-src.zip"))
    }

    override def docUrl (loc :Loc) :String = {
      val path = loc.path flatMap(_ split("\\.")) mkString("/")
      // handle JFX docs being in special place; sigh...
      if (jvm.platformVers.toInt == 8 && path.contains("javafx"))
        s"http://download.java.net/jdk8/jfxdocs/$path.html"
      else s"http://docs.oracle.com/javase/${jvm.platformVers}/docs/api/$path.html"
    }

    // def needsReindex (lastIndexed :Long) = sourceExists(_.lastModified > lastIndexed)
    override protected def sourceExists (p :File => Boolean) = false // TODO
  }

  abstract class POMProjectModel (root :File, val pfile :File) extends ProjectModel(root) {
    override def isValid    = pfile.exists
    override def name       = _pom.name getOrElse root.getName
    override def groupId    = _pom.groupId
    override def artifactId = _pom.artifactId
    override def version    = _pom.version

    override def depends = _pom.transitiveDepends(true) map { d =>
      Depend(d.groupId, d.artifactId, d.version, "m2", d.scope == "test", None)
    }

    override def needsReload (lastLoaded :Long) = pfile.lastModified > lastLoaded

    protected lazy val _pom = POM.fromFile(pfile).get
  }

  class MavenProjectModel (root :File) extends POMProjectModel(root, file(root, "pom.xml")) {
    override val flavor   = "maven"
    override def isRemote = false

    override def depends = {
      val basedeps = super.depends
      val needScalaDep = sourceExists(_.getName.endsWith(".scala"))
      val needJavaDep = needScalaDep || sourceExists(_.getName.endsWith(".java"))
      def haveJavaDep = basedeps.exists(d => d.groupId == "java" && d.artifactId == "jdk")
      def haveScalaDep = basedeps.exists(d => d.groupId == "org.scala-lang" &&
        d.artifactId == "scala-library")
      basedeps ++
      (if (needJavaDep && !haveJavaDep) Seq(javaDepend) else Seq()) ++
      (if (needScalaDep && !haveScalaDep) Seq(scalaDepend) else Seq())
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

    private def scalaDepend = {
      val scalaVers = _pom.getAttr("scala.version") getOrElse("2.10.1")
      Depend("org.scala-lang", "scala-library", scalaVers, "m2", false, None)
    }
    // TODO: deduce JDK version from POM
    private def javaDepend = JVMs.findDepend(JVMs.runtimeVers)

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
      val url = new URL(s"http://central.maven.org/maven2/$gpath/$aid/$vers/$aid-$vers-$cfier.jar")
      downloader request(_.download(url, artifact(cfier)))
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
      val basedeps = _extracted.get("library-dependencies") map(SBT.parseDeps) getOrElse(Seq())
      val needScalaDep = sourceExists(_.getName.endsWith(".scala"))
      val needJavaDep = needScalaDep || sourceExists(_.getName.endsWith(".java"))
      def haveJavaDep = basedeps.exists(d => d.groupId == "java" && d.artifactId == "jdk")
      def haveScalaDep = basedeps.exists(d => d.groupId == "org.scala-lang" &&
        d.artifactId == "scala-library")
      basedeps ++
      (if (needJavaDep && !haveJavaDep) Seq(javaDepend) else Seq()) ++
      (if (needScalaDep && !haveScalaDep) Seq(scalaDepend) else Seq())
    }

    override def hasDocs = file("target", "api").exists
    override def tryGenerateDocs () = SBT.buildDocs(root)

    // TODO: if any .java file in project/ changes, we need a reload
    override def needsReload (lastLoaded :Long) = _bfile.lastModified > lastLoaded

    override protected def srcDir =
      (_extracted.get("compile:source-directory") map(new File(_))) orElse super.srcDir
    override protected def testSrcDir =
      (_extracted.get("test:source-directory") map(new File(_))) orElse super.testSrcDir

    private def scalaDepend = {
      val scalaVers = _extracted.get("scala-version") getOrElse("2.10.1")
      Depend("org.scala-lang", "scala-library", scalaVers, "ivy", false, None)
    }
    // TODO: deduce JDK version from javacOptions (e.g. -source 6)
    private def javaDepend = JVMs.findDepend(JVMs.runtimeVers)

    // TODO: are there better ways to detect SBT? exists(project/*.scala)?
    private lazy val _bfile = Seq(file("build.sbt"), file("project", "Build.scala")) find(
      _.exists) getOrElse(file("build.sbt"))

    private lazy val _extracted = SBT.extractProps(
      root, "name", "organization", "version", "scala-version", "library-dependencies",
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
      // val url = new URL(s"http://central.maven.org/maven2/$gpath/$aid/$vers/$aid-$vers-$cfier.jar")
      // log.info(s"Downloading $url...")
      // downloader request(_.download(url, artifact(cfier))
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

    override def docUrl (loc :Loc) :String = {
      // for example: http://msdn.microsoft.com/en-us/library/system.convert.aspx
      s"http://msdn.microsoft.com/en-us/library/${loc.qualName.toLowerCase}.aspx"
    }

    lazy val _srcDir = file("mono", "mcs", "class")
  }

  // we are constructed with our .csproj file rather than its containing directory
  class CSProjectModel (csproj :File) extends DefaultProjectModel(csproj.getParentFile) {
    override val flavor     = "cs"
    override def isValid    = csproj.getName.endsWith(".csproj") && csproj.isFile
    override def isRemote   = false
    override def name       = csproj.getName dropRight ".csproj".length
    override def groupId    = _info.rootNamespace
    override def artifactId = _info.assemblyName
    override def version    = _info.version

    override def depends = {
      // TODO: do .csproj files have any notion of test dependencies?
      val deps = _info.refs map(_.toDepend(false))
      // if this is monotouch project, add mtCoreDep and filter out all DLL deps that it subsumes
      if (_info.refs.exists(_.name == "monotouch"))
        mtCoreDep +: (deps filterNot(d => mtCoreDlls(d.artifactId)))
      else deps
    }

    override def applyToSource (f :Boolean => File => Unit) {
      _info.sources foreach f(false) // TODO: how to differentiate test sources?
    }

    override protected def sourceExists (p :File => Boolean) = _info.sources exists p

    // override def hasDocs = file("target", "api").exists
    // override def tryGenerateDocs () = SBT.buildDocs(root)

    private lazy val _info = CSProj.parse(csproj)
  }

  class DllProjectModel (dll :File) extends ProjectModel(dll) {
    override val flavor     = "dll"
    override def isValid    = dll.isFile
    override def isRemote   = true
    override def name       = _info.name
    override def groupId    = _info.name
    override def artifactId = dll.getName dropRight ".dll".length
    override def version    = _info.version
    override def depends    = Seq[Depend]() // no idea!

    override def applyToSource (f :Boolean => File => Unit) = f(false)(dll)
    override protected def sourceExists (p :File => Boolean) = p(dll)

    override def docUrl (loc :Loc) :String = {
      // TODO: only for monotouch &c depends
      s"http://docs.go-mono.com/?link=T%3a${loc.qualName}%2f*"
    }

    private lazy val _info = Monodis.assemblyInfo(dll)
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

  private[extract] val mtCoreDep = Depend(
    "com.xamarin", "monotouch-core", "0", "mtcore", false, None)
  private[extract] val mtCoreDlls = Set("System", "System.Core") // TODO: moar!

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
