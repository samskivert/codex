//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File

import codex.data.Depend

/** Helper routines for dealing with DLLs. */
object Dll {

  /** Finds the DLL for the specified dependency, if possible. */
  def find (dep :Depend) :Option[File] = dep.hintFile orElse find(dep.artifactId)

  /** Searches the standard installation spots for the specified DLL. */
  def find (name :String) :Option[File] = {
    // TODO: look in other standard places? check MONO_PATH? handle versions?
    // oh C# is such a wild and wooly development ecosystem
    val mtLib = new File(s"/Developer/MonoTouch/usr/lib/mono/2.1/$name.dll")
    if (mtLib.exists) Some(mtLib)
    else None
  }
}
