//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import org.junit.Assert._
import org.junit._

import codex._

class ProjectModelTest {

  @Test def testFamily {
    ProjectModel.forRoot(file(home, "projects", "playn", "core")) foreach { pm =>
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
    ProjectModel.forRoot(file(home, "projects", "playn", "tests", "android")) foreach { pm =>
      assertTrue(pm.isValid)
      assertFalse(pm.isRemote)
      val family = pm.family
      assertTrue(family exists (_.getPath contains("core")))
      assertTrue(family exists (_.getPath contains("java")))
      assertTrue(family exists (_.getPath contains("tests/android")))
    }
  }
}
