//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File

/** Utilities for interacting with Maven. */
object Maven {

  /** Invokes `mvn javadoc:javadoc` in `root` with the aim of generating javadocs. */
  def buildDocs (root :File) {
    Shell.shell(root, "mvn", "javadoc:javadoc") !
  }
}
