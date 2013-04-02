//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, StringReader}
import org.junit.Assert._
import org.junit._
import scala.collection.mutable.ArrayBuffer

class ClikeTest {

  @Test def testSomeJava {
    val out = test(new ClikeExtractor {
      override val lang = "java"
    }, """
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
ENTER package com.test 2
EXIT com.test
ENTER class Foo 4
 ENTER class Bar 5
 EXIT Bar
 ENTER interface Bippy 9
 EXIT Bippy
EXIT Foo
""".substring(1), out)
  }

  @Test def testSomeScala {
    val out = test(new ClikeExtractor {
      override val lang = "scala"
    }, """
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
ENTER package com.test 2
 ENTER object Foo 4
  ENTER class Bar 5
   ENTER def baz 6
   EXIT baz
  EXIT Bar
  ENTER trait Bippy 9
   ENTER def bangle 10
   EXIT bangle
  EXIT Bippy
  ENTER def fiddle 12
  EXIT fiddle
  ENTER def faddle 13
   ENTER def nested1 14
   EXIT nested1
   ENTER def nested2 15
   EXIT nested2
  EXIT faddle
 EXIT Foo
 ENTER def outer 19
 EXIT outer
""".substring(1), out)
  }

  private def test (ex :Extractor, code :String) = {
    val buf = new StringBuilder
    ex.process("test", new StringReader(code), new Visitor {
      def onCompUnit (path :File) = dump("CU $path")
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
    })
    buf.toString
  }
}
