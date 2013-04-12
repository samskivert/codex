//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import org.junit.Assert._
import org.junit._

import codex._

class CSProjTest {

  @Test def parseProjTest {
    val csproj = file(home, "ops", "monomac", "samples", "macdoc", "macdoc.csproj")
    if (csproj.exists) {
      val info = CSProj.parse(csproj)
      assertEquals("macdoc", info.rootNamespace)
      assertEquals("macdoc", info.assemblyName)
      assertTrue(info.refs.contains(CSProj.Reference("System", None)))
      // info.refs foreach println
    }
  }
}
