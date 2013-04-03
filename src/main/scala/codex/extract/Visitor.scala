//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File

/** Name extraction operates via the visitor pattern. The underlying extractor visits every
  * compilation unit in question, calling `onCompUnit` when a compilation unit is first entered,
  * then calling `onEnter` when an element is entered and `onExit` when that element's scope is
  * exited. If a new element scope is entered before leaving the current element's scope, that
  * element can be said to be ''owned'' by the outer element. For example, the following code:
  * {{{
  * module Foo {
  *   class Bar {
  *     class Baz {
  *       def init ()
  *       def shutdown ()
  *     }
  *   }
  * }
  * }}}
  * will result in the following sequence of enter/exits (indentation shown for clarity):
  * {{{
  * onEnter("Foo", "module", ...)
  *  onEnter("Bar", "class", ...)
  *   onEnter("Baz", "class", ...)
  *    onEnter("init", "def", ...)
  *    onExit()
  *    onEnter("shutdown", "def, ...)
  *    onExit()
  *   onExit()
  *  onExit()
  * onExit()
  * }}}
  */
trait Visitor {

  /** Called when a compilation unit is about to be processed. */
  def onCompUnit (path :String)

  /** Called when we enter the ''scope'' of a new element. */
  def onEnter (name :String, kind :String, offset :Int)

  /** Called when we exit the scope of the most recently [[onEnter]]ed element. */
  def onExit (name :String)
}
