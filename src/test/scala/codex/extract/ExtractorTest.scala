//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, StringReader}
import org.junit.Assert._
import org.junit._

class ExtractorTest {
  import ExtractorTest._

  @Test def testJarReading {
    val buf = new StringBuilder
    Extractor.extract(new File("/Users/mdb/.m2/repository/javax/servlet/servlet-api/2.5/servlet-api-2.5-sources.jar"), dumper(buf))
    println(buf)
  }
}

object ExtractorTest {

  def dumper (buf :StringBuilder) = new Visitor {
    def onCompUnit (path :String) = {
      indent = 0
      dump(s"CU $path")
    }
    def onEnter (name :String, kind :String, offset :Int) {
      dump(s"ENTER $kind $name $offset")
      indent += 1
    }
    def onExit (name :String) {
      indent -= 1
      dump(s"EXIT $name")
    }
    private def dump (msg :String) = buf.append(" " * indent).append(msg).append("\n")
    private var indent = 0
  }

  def test (ex :Extractor, code :String) = {
    val buf = new StringBuilder
    ex.process(dumper(buf), "test", new StringReader(code))
    buf.toString
  }
}
