seq(samskivert.POMUtil.pomToSettings("pom.xml") :_*)

seq(Revolver.settings :_*)

autoScalaLibrary := false // we get scala-library from the POM

crossPaths := false

scalacOptions ++= Seq("-feature", "-language:postfixOps")

// allows SBT to run junit tests
libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test->default"
