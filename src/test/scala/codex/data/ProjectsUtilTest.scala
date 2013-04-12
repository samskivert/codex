//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import org.junit.Assert._
import org.junit._

import codex._

class ProjectsUtilTest {
  import ProjectsUtil.findRoot

  @Test def detectRootTest {
    val sam = file(home, "projects", "samskivert")
    if (sam.isDirectory) {
      assertEquals(Some(sam), findRoot(file(sam, "src")))
      assertEquals(Some(sam), findRoot(file(sam, "noexisty")))
    }
    val macdoc = file(home, "ops", "monomac", "samples", "macdoc")
    if (macdoc.isDirectory) {
      assertEquals(Some(file(macdoc, "macdoc.csproj")), findRoot(macdoc))
      val docwiz = file(macdoc, "AppleDocWizard")
      assertEquals(Some(file(docwiz, "AppleDocWizard.csproj")), findRoot(docwiz))
      assertEquals(Some(file(docwiz, "AppleDocWizard.csproj")), findRoot(file(docwiz, "bin")))
    }
  }
}
