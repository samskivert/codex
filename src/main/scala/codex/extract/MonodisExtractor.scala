//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, InputStream, InputStreamReader, Reader, StreamTokenizer}
import scala.collection.mutable.ArrayBuffer

import codex._

/** Extracts CLR type information from DLLs. */
class MonodisExtractor extends Extractor {
  import Clike._

  override def process (viz :Visitor, isTest :Boolean, file :File) {
    // we grind the monodis output on another thread, so we need to build up a giant list of
    // callback actions and then all all of those on our current thread, assuming everything is
    // groovy; though the callback actions list is large, it's not nearly as large as dumping the
    // entire monodis output into one big buffer and processing it on the calling thread
    val thunks = ArrayBuffer[() => Unit]()
    def onEnter (name :String, kind :String, off :Int) = {
      thunks += { () => viz.onEnter(name, kind, off) }
    }
    def onExit (name :String) = {
      thunks += { () => viz.onExit(name) }
    }

    val path = file.getAbsolutePath
    viz.onCompUnit(path, false)
    Monodis.invoke(file.getParentFile, path) { mdis :Reader =>
      var prevtok :String = null
      var curdef :String = null
      var blocks :List[String] = Nil
      var public = false

      val tok = toker(mdis)
      while (tok.nextToken() != StreamTokenizer.TT_EOF) {
        if (tok.ttype == '{') {
          // note that we entered a block for our most recent def (which may be null)
          blocks = curdef :: blocks
          // and clear out curdef so that nested blocks for this def are ignored
          curdef = null

        } else if (tok.ttype == '}') {
          if (blocks.isEmpty) {
            log.warning("Mismatched close brace", "line", tok.lineno-1)
          } else {
            // if this block was associated with a def, tell the viz we're exiting it
            if (blocks.head != null) onExit(blocks.head)
            blocks = blocks.tail
          }

        } else if (tok.ttype == StreamTokenizer.TT_WORD || tok.ttype == '\'') {
          if (prevtok == "namespace") {
            onEnter(tok.sval, prevtok, tok.lineno-1)
            curdef = tok.sval

          } else if (tok.sval == "class") {
            // we'll eventually see a class, so reset our attributes
            public = false

          } else if (tok.sval == "public") {
            public = true

          } else if (tok.sval == "extends") {
            if (public) {
              onEnter(prevtok, "class", tok.lineno-1)
              curdef = prevtok
            } // else log.info(s"Skipping private class $prevtok")
          }

          prevtok = tok.sval
        }
      }
    }

    thunks foreach { _.apply() }
  }

  override def process (viz :Visitor, isTest :Boolean, unitName :String, reader :Reader) {
    throw new UnsupportedOperationException
  }

  private def parse (viz :Visitor, isTest :Boolean)(in :Reader) :Unit = try {

  } finally {
    in.close()
  }
}
