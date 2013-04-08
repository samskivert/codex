import sbt._

object CodexBuild extends Build {
  import ProguardPlugin._
  import java.io.File
  import sbt.Keys._
  import com.threerings.getdown.tools.Digester

  val passThroughLibs = TaskKey[Seq[File]](
    "passthrough-libs", "Library dependencies that we don't Proguard and put straight into Getdown")

  val getdown = TaskKey[Unit]("getdown", "Generates Getdown client")
  private def getdownTask = (sourceDirectory in Compile, dependencyClasspath in Compile,
                             passThroughLibs in Compile, minJarPath in Compile, target,
                             streams) map {
    (srcDir, depCP, passLibs, minJarPath, target, s) =>

    val dest = (target / "getdown").asFile
    def copy (files :Iterable[File]) = IO.copy(files.map(src => (src.asFile, dest / src.getName)))

    // copy our metadata files
    copy((srcDir / "getdown" * "*").get)
    // copy our non-proguarded libs
    copy(passLibs)
    // copy our proguarded code jar
    IO.copy(Seq((minJarPath, dest / "codex.jar")))
    // add a copy of getdown as getdown-new.jar
    depCP map(_.data) foreach { case dep =>
      if (dep.getName startsWith "getdown") IO.copy(Seq((dep, dest / "getdown-new.jar")))
    }

    // finally create our digest
    try Digester.createDigest(dest)
    catch {
      case e :Throwable =>
        s.log.warn("Digest creation failed " + e)
        throw e
    }
  }

  val upload = TaskKey[Unit]("upload", "Uploads Getdown client to samskivert.com")
  private def uploadTask = (target, streams) map { (target, s) =>
    val dest = (target / "getdown").asFile.getAbsolutePath
    "rsync -avr " + dest + "/ samskivert.com:/export/samskivert/pages/codex/client" ! ; ()
  }

  val codexSettings = seq(
    autoScalaLibrary := false, // we get scala-library from the POM
    crossPaths := false,
    scalacOptions ++= Seq("-feature", "-language:postfixOps"),

    // allows SBT to run junit tests
    libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test->default",

    // various proguard jiggery pokery
    proguardOptions := Seq(
      keepMain("codex.Codex"),
      "-keep public class net.sf.cglib.** { *; }",
      "-keep public class codex.** { *; }",
      "-keep class org.squeryl.** { *; }",
      "-keep class net.sf.cglib.** { *; }",
      "-keep class scala.Function1",
      "-dontnote scala.**"
    ),
    proguardInJars := Seq(),
    passThroughLibs <<= (dependencyClasspath in Compile) map (_ map(_.data) collect {
      case file if (file.getName startsWith "scala-library") => file
    }),
    proguardLibraryJars <++= passThroughLibs,
    makeInJarFilter <<= (makeInJarFilter) { (makeInJarFilter) => { (file) => file match {
      case "javax.servlet-3.0.0.v201112011016.jar" => makeInJarFilter(file) + ",!META-INF/**"
      case _ => makeInJarFilter(file)
    }}},

    // tasks for generating and uploading getdown client
    getdown <<= getdownTask dependsOn proguard,
    upload  <<= uploadTask dependsOn getdown
  )

  lazy val root = Project("codex", file("."), settings = {
    Project.defaultSettings ++
    samskivert.POMUtil.pomToSettings("pom.xml") ++
    proguardSettings ++
    codexSettings ++
    spray.revolver.RevolverPlugin.Revolver.settings
  })
}
