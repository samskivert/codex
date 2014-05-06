import sbt._

object CodexBuild extends Build {
  import java.io.File
  import sbt.Keys._
  import com.typesafe.sbt.SbtProguard._
  import com.threerings.getdown.tools.Digester

  val getdown = TaskKey[Unit]("getdown", "Generates Getdown client")
  private def getdownTask = (sourceDirectory in Compile, dependencyClasspath in Compile,
                             artifactPath in Proguard, target, streams) map {
    (srcDir, depCP, minJarPath, target, s) =>

    val dest = (target / "getdown").asFile
    def copy (files :Iterable[File]) = IO.copy(files.map(src => (src.asFile, dest / src.getName)))

    // copy our metadata files
    copy((srcDir / "getdown" * "*").get)
    // copy our non-proguarded libs (and strip their Ivy/Maven version in the process)
    for (passLib <- depCP ; if (passThrough(passLib.data))) {
      val tgtName = passLib.get(moduleID.key).get.name + ".jar"
      IO.copy(Seq((passLib.data, dest / tgtName)))
    }
    // copy our elisp files
    IO.copy((srcDir / "elisp" * "*").get.map(src => (src.asFile, dest / "elisp" / src.getName)))

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

  val passThrough = (_ :File).getName startsWith "scala-library"

  val codexSettings = seq(
    autoScalaLibrary := false, // we get scala-library from the POM
    crossPaths := false,
    scalacOptions ++= Seq("-feature", "-language:postfixOps"),

    // allows SBT to run junit tests
    libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test->default",
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),

    // tasks for generating and uploading getdown client
    getdown <<= getdownTask, // dependsOn (Proguard, ProguardKeys.proguard),
    upload  <<= uploadTask dependsOn getdown
  )

  // various proguard jiggery pokery
  import ProguardKeys._
  val codexProguardSettings = seq(
    proguardVersion in Proguard := "4.11",
    options in Proguard += ProguardOptions.keepMain("codex.Codex"),
    options in Proguard ++= Seq(
      "-dontoptimize",
      "-dontobfuscate",
      "-keep class net.sf.cglib.** { *; }",
      "-keep class org.squeryl.** { *; }",
      "-keep class scala.Function1",
      "-keep public class codex.** { *; }",
      "-dontwarn net.sf.cglib.**",
      "-dontwarn samscala.util.Log*",
      "-dontwarn com.threerings.getdown.tools.**",
      "-dontwarn com.threerings.getdown.launcher.**",
      "-dontwarn com.threerings.getdown.launcher.GetdownApplet",
      "-dontnote org.squeryl.internals.**",
      "-dontnote org.h2.**",
      "-dontwarn org.h2.fulltext.**",
      "-dontwarn org.h2.jcr.Railroads",
      "-dontwarn org.h2.message.TraceWriterAdapter",
      "-dontwarn org.h2.util.DbDriverActivator",
      "-dontnote org.eclipse.**",
      "-dontwarn org.eclipse.jetty.util.log.**",
      "-dontwarn org.eclipse.jetty.server.handler.jmx.**",
      "-dontwarn org.eclipse.jetty.server.jmx.**",
      "-dontwarn org.eclipse.jetty.server.session.jmx.**",
      "-dontwarn org.eclipse.jetty.servlet.jmx.**",
      "-dontwarn ch.epfl.lamp.compiler.**",
      "-dontnote scala.**",
      "-dontwarn scala.**"
    ),
    inputs in Proguard ~= { _ filterNot passThrough },
    inputFilter in Proguard := { _.name match {
      case nm if (nm.startsWith("jetty-"))        => Some("!META-INF/MANIFEST.MF,!*.html")
      case nm if (nm.startsWith("javax.servlet")) => Some("!META-INF/**")
      case _                                      => Some("!META-INF/MANIFEST.MF")
    }},
    // for fuck's sake sbt and sbt-proguard authors!
    javaOptions in (Proguard, proguard) := Seq("-mx1024M")
  )

  lazy val root = Project("codex", file("."), settings = {
    Project.defaultSettings ++
    samskivert.POMUtil.pomToSettings("pom.xml") ++
    proguardSettings ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings ++
    spray.revolver.RevolverPlugin.Revolver.settings ++
    codexSettings ++
    codexProguardSettings
  })
}
