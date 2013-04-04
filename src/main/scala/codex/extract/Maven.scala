//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File

/** Utilities for interacting with Maven. */
object Maven {
  import scala.sys.process._

  /** Invokes `mvn javadoc:javadoc` in `root` with the aim of generating javadocs. */
  def buildDocs (root :File) {
    Process(Seq("mvn", "javadoc:javadoc"), root) !
  }
}
