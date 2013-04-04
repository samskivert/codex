//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{BufferedReader, Reader, StreamTokenizer}

/** Contains some useful bits for working with C-like source files. */
object Clike {

  /** Tokens that will appear prior to an element declaration, by language. */
  val kindsByLang = Map("java"  -> Set("class", "enum", "interface", "@interface"),
                        "scala" -> Set("class", "object", "trait", "def"),
                        "as"    -> Set("class", "interface"),
                        "cs"    -> Set("class", "enum", "interface", "struct"))

  /** Creates a [[StreamTokenizer]] configured for parsing C-like source code. */
  def toker (reader :Reader) = {
    val tok = new StreamTokenizer(new BufferedReader(reader))
    tok.ordinaryChar('/') // why do they call this a comment char by default?
    tok.wordChars('_', '_') // allow _ in class names
    tok.slashSlashComments(true)
    tok.slashStarComments(true)
    tok
  }
}
