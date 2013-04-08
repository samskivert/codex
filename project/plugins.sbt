libraryDependencies += "com.samskivert" % "sbt-pom-util" % "0.4-SNAPSHOT"

addSbtPlugin("io.spray" % "sbt-revolver" % "0.6.2")

resolvers += Resolver.url("sbt-plugin-releases-scalasbt", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.scala-sbt" % "xsbt-proguard-plugin" % "0.1.3")

// for our getdown task
libraryDependencies += "com.threerings" % "getdown" % "1.3"
