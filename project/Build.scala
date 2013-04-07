import sbt.Keys._
import sbt._

object CodexBuild extends Build {

  val codexSettings = seq(
    autoScalaLibrary := false, // we get scala-library from the POM
    crossPaths := false,
    scalacOptions ++= Seq("-feature", "-language:postfixOps"),
    // allows SBT to run junit tests
    libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test->default"
  )

  lazy val root = Project("codex", file("."), settings = {
    Project.defaultSettings ++
    samskivert.POMUtil.pomToSettings("pom.xml") ++
    codexSettings ++
    spray.revolver.RevolverPlugin.Revolver.settings
  })
}
