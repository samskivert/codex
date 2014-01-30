libraryDependencies += "com.samskivert" % "sbt-pom-util" % "0.6"

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")

// for our getdown task
libraryDependencies += "com.threerings" % "getdown" % "1.3"

// used to debug dependencies
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")
