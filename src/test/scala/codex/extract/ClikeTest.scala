//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import org.junit.Assert._
import org.junit._

class ClikeTest {
  import ExtractorTest._

  @Test def testSomeJava {
    val out = test(new ClikeExtractor("java"), """
package com.test

public class Foo {
  public class Bar {
    public void baz () {}
    public int BAZ = 1;
  }
  public interface Bippy {
    void bangle ();
  }
  public void fiddle () {
  }
}
""")
    assertEquals("""
CU test
ENTER package com.test 1
EXIT com.test
ENTER class Foo 3
 ENTER class Bar 4
 EXIT Bar
 ENTER interface Bippy 8
 EXIT Bippy
EXIT Foo
""".substring(1), out)
  }

  @Test def testSomeScala {
    val out = test(new ClikeExtractor("scala"), """
package com.test

object Foo {
  class Bar {
    def baz () {}
    val BAZ = 1
  }
  trait Bippy {
    def bangle ()
  }
  def fiddle (foo :Int, bar :Int) = monkey
  def faddle (one :Int, two :String) = {
    def nested1 (thing :Bippy) = ...
    def nested2 (thing :Bippy) = {}
  }
}

def outer (thing :Bippy) = ...
""")
    assertEquals("""
CU test
ENTER package com.test 1
 ENTER object Foo 3
  ENTER class Bar 4
   ENTER def baz 5
   EXIT baz
  EXIT Bar
  ENTER trait Bippy 8
   ENTER def bangle 9
   EXIT bangle
  EXIT Bippy
  ENTER def fiddle 11
  EXIT fiddle
  ENTER def faddle 12
   ENTER def nested1 13
   EXIT nested1
   ENTER def nested2 14
   EXIT nested2
  EXIT faddle
 EXIT Foo
 ENTER def outer 18
 EXIT outer
""".substring(1), out)
  }
}
