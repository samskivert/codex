//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import org.junit.Assert._
import org.junit._

import codex.data.Depend

class SBTTest {

  @Test def parseDepsTest {
    val deps = ("List(org.scala-lang:scala-library:2.10.1, " +
      "com.samskivert:samscala:1.0-SNAPSHOT, " +
      "com.samskivert:pom-util:0.4-SNAPSHOT, " +
      "org.eclipse.jetty:jetty-servlet:9.0.0.RC2, " +
      "org.squeryl:squeryl_2.10:0.9.5-6, " +
      "com.h2database:h2:1.2.127, junit:junit:4.8.1:test, " +
      "com.novocode:junit-interface:0.7:test->default)")
    assertEquals(Seq(Depend("org.scala-lang", "scala-library", "2.10.1", "ivy", false),
                     Depend("com.samskivert", "samscala", "1.0-SNAPSHOT", "ivy", false),
                     Depend("com.samskivert", "pom-util", "0.4-SNAPSHOT", "ivy", false),
                     Depend("org.eclipse.jetty", "jetty-servlet", "9.0.0.RC2", "ivy", false),
                     Depend("org.squeryl", "squeryl_2.10", "0.9.5-6", "ivy", false),
                     Depend("com.h2database", "h2", "1.2.127", "ivy", false),
                     Depend("junit", "junit", "4.8.1", "ivy", true),
                     Depend("com.novocode", "junit-interface", "0.7", "ivy", true)),
                 SBT.parseDeps(deps))
  }
}
