//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import org.junit.Assert._
import org.junit._

import codex._

class MonodisTest {
  import ExtractorTest._

  @Test def testExtraction {
    val dll = new File("/Developer/MonoTouch/usr/lib/mono/2.1/Mono.Security.dll")
    if (dll.exists) {
      val buf = new StringBuilder
      Extractor.extract(dumper(buf))(false)(dll)
      val out = buf.toString
      assertTrue(out.contains("ENTER namespace Crimson.CommonCrypto"))
      // TODO: more serious tests?
    }
  }

  @Test def testInfo {
    val dll = new File("/Developer/MonoTouch/usr/lib/mono/2.1/Mono.Security.dll")
    if (dll.exists) {
      println(Monodis.assemblyInfo(dll))
    }
  }
}
