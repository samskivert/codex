//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, FileReader, FileInputStream, InputStreamReader, Reader}
import java.util.jar.JarInputStream
import scala.collection.mutable.{Set => MSet}

import codex._

/** Coordinates the process of extracting name information from a projects' source files. This
  * includes identifying which files are source files, what language they are, and parsing the
  * files and extracting name and kind info. The final phase is sometimes handled by the compiler
  * infrastructure for the language in question.
  */
object Extractor {

  /** Extracts elements from the supplied file, invoking the appropriate methods on `visitor`. */
  def extract (visitor :Visitor)(isTest :Boolean)(file :File) {
    // if the source is a jar file, operate on its contents
    if (file.getName.endsWith(".jar")) {
      val jin = new JarInputStream(new FileInputStream(file))
      var entry = jin.getNextJarEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val name = entry.getName
          Exors.get(suff(name)) foreach(
            _.process(visitor, isTest, file.getPath + "!" + name, new InputStreamReader(jin)))
        }
        entry = jin.getNextJarEntry
      }
    }
    // otherwise it's a single source file, so process it
    else Exors.get(suff(file)) foreach { _.process(visitor, isTest, file) }
  }

  private def suff (file :File) :String = suff(file.getName)
  private def suff (name :String) :String = name.substring(name.lastIndexOf(".") + 1)

  private val Exors = Map(
    "java"  -> new ClikeExtractor("java"),
    "scala" -> new ClikeExtractor("scala"),
    "cs"    -> new ClikeExtractor("cs"),
    "as"    -> new ClikeExtractor("as"),
    "dll"   -> new MonodisExtractor
  )
}

trait Extractor {
  def process (visitor :Visitor, isTest :Boolean, file :File) {
    process(visitor, isTest, file.getPath, new FileReader(file))
  }

  def process (visitor :Visitor, isTest :Boolean, unitName :String, reader :Reader)
}
