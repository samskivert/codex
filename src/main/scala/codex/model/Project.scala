//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.model

import java.io.File

class Project(
  /** The directory at which this project is rooted. */
  val root :File,
  /** The projects on which this project depends. */
  val depends :Seq[FqId]
)
{
}
