//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{BufferedReader, File, Reader, StreamTokenizer}

import codex._

/** A base class for very rudimentary extraction of names from C-like languages.
  * Currently supports Java, Scala, C# and ActionScript. */
class ClikeExtractor (lang :String) extends Extractor {
  import Clike._

  override def process (visitor :Visitor, isTest :Boolean, unitName :String, reader :Reader) {
    visitor.onCompUnit(unitName, isTest)

    val kinds = kindsByLang(lang)
    var prevtok :String = null
    var curdef :String = null
    var blocks :List[String] = Nil

    val tok = toker(reader)
    // treat # as a line comment starter in C# so that we ignore compiler directives
    if (lang == "cs") tok.commentChar('#')

    while (tok.nextToken() != StreamTokenizer.TT_EOF) {
      if (tok.ttype == '{') {
        // note that we entered a block for our most recent def
        blocks = curdef :: blocks
        // and clear out curdef so that nested blocks for this def are ignored
        curdef = null

      } else if (tok.ttype == '}') {
        // if our previous def had no block associated with it, we're exiting it now
        if (curdef != null) {
          visitor.onExit(curdef)
          curdef = null
        }

        if (blocks.isEmpty) {
          log.warning("Mismatched close brace", "file", unitName, "line", tok.lineno-1)
        } else {
          // if this block was associated with a def, tell the visitor we're exiting it
          if (blocks.head != null) visitor.onExit(blocks.head)
          blocks = blocks.tail
        }

      } else if (tok.ttype == StreamTokenizer.TT_WORD) {
        if (prevtok == "package" || prevtok == "namespace") {
          visitor.onEnter(tok.sval, prevtok, tok.lineno-1)
          curdef = tok.sval
          // if the next token is a semicolon (or if this is Scala and the next token is not an
          // open bracket), pretend the rest of the file is one big block
          val ntok = tok.nextToken()
          if (ntok == ';' || (ntok != '{' && lang == "scala")) {
            blocks = curdef :: blocks
            curdef = null
          } else {
            tok.pushBack()
          }

        } else if (kinds(prevtok)) {
          // if our previous def had no block associated with it, we're exiting it now
          if (curdef != null) visitor.onExit(curdef)
          // tack a $ onto Scala object names; TODO: abstract this more nicely
          val name = tok.sval + (if (prevtok == "object") "$" else "")
          visitor.onEnter(name, prevtok, tok.lineno-1)
          curdef = tok.sval
        }
        prevtok = tok.sval
      }
    }

    // if our last def had no block associated with it, we're exiting it now
    if (curdef != null) visitor.onExit(curdef)
  }

  private def suffix (name :String) = name.substring(name.lastIndexOf(".")+1)
}
