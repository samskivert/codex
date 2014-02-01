//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import scala.io.Source

import codex.data.Depend

/** Utilities for finding and interacting with locally installed JVMs. */
object JVMs {

  class JVM (
    /** The root directory in which this JVM is installed. */
    val root :File
  ) {
    /** This JVM's version. For example: `1.7.0`. */
    val version :String = {
      val rel = new File(root, "release")
      (if (rel.exists) extractVers(rel) else None) getOrElse {
        import scala.sys.process._
        Seq(new File(root, "bin/java").getPath, "-fullversion") !!
      }
    }

    /** The Java platform version for this JVM (e.g. 5, 6, 7, 8). */
    def platformVers :String = version match {
      case v if (v startsWith "1.5") => "5"
      case v if (v startsWith "1.6") => "6"
      case v if (v startsWith "1.7") => "7"
      case v if (v startsWith "1.8") => "8"
      case _ => "7"
    }
  }

  lazy val jvms :Seq[JVM] = {
    // TODO: other platforms, make use of System.getProperty("java.home")
    val root = new File("/Library/Java/JavaVirtualMachines")
    root.listFiles map(new File(_, "Contents/Home")) map(new JVM(_))
  }

  val runtimeVers = {
    val v = System.getProperty("java.runtime.version")
    v indexOf '_' match {
      case -1 => v
      case ix => v.substring(0, ix)
    }
  }

  def findDepend (vers :String) :Depend = {
    val depvers = findBestMatch(vers) map(_.version) getOrElse(runtimeVers)
    Depend("java", "jdk", depvers, "jdk", false, None)
  }

  def findBestMatch (vers :String) :Option[JVM] = {
    val exact = jvms find(_.version == vers) // an exact match 1.6 == 1.6
    val prefix = jvms find(_.version startsWith vers) // a prefix match 1.6 =~ 1.6.1
    exact orElse prefix orElse jvms.headOption
  }

  // release file contains a line like JAVA_VERSION="1.7.0"; this matches and extracts 1.7.0
  private def extractVers (relfile :File) =
    Source.fromFile(relfile) getLines() find(_ startsWith "JAVA_VERSION") map(_ split "=") match {
      case Some(Array(_, vers)) => Some(vers.replaceAll("\"", "")) // strip " from "1.x.0"
      case _ => None
    }
}
