//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import org.junit.Assert._
import org.junit._

import codex._

class ProjectModelTest {
  import ProjectModel._

  @Test def testFamily {
    forRoot(file(home, "projects", "playn", "core")) foreach { pm =>
      assertTrue(pm.isValid)
      assertFalse(pm.isRemote)
      assertEquals("PlayN Core", pm.name)
      assertEquals("com.googlecode.playn", pm.groupId)
      // println(pm.version)
      // println(pm.sourceDir)
      // println(pm.testSourceDir)

      val family = pm.family
      assertTrue(family exists (_.getPath contains("core")))
      assertTrue(family exists (_.getPath contains("java")))
      assertTrue(family exists (_.getPath contains("tests/android")))
    }
    forRoot(file(home, "projects", "playn", "tests", "android")) foreach { pm =>
      assertTrue(pm.isValid)
      assertFalse(pm.isRemote)
      val family = pm.family
      assertTrue(family exists (_.getPath contains("core")))
      assertTrue(family exists (_.getPath contains("java")))
      assertTrue(family exists (_.getPath contains("tests/android")))
    }
  }

  @Test def mtcoreDepTest {
    assertTrue(forDepend(mtCoreDep).isDefined)
  }

  @Test def csprojDepsTest {
    val macdoc = file(home, "ops", "monomac", "samples", "macdoc", "macdoc.csproj")
    forRoot(macdoc) foreach { pm =>
      // println(pm.depends)
    }
    val tfhp = file(home, "projects", "twentyfour", "client", "ios", "twentyfour.csproj")
    forRoot(tfhp) foreach { pm =>
      assertTrue(pm.depends.contains(mtCoreDep))
      // println(pm.depends)
    }
  }
}
