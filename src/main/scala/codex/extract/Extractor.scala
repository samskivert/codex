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

  /** Recurses over the supplied source root, finding all files that match the language handled by
    * this extractor, parses the files and invokes the appropriate methods on `visitor` as it goes.
    */
  def extract (root :File, visitor :Visitor) {
    log.info(s"Extracting metadata from ${root.getPath}...")
    // if the source root is a directory, recurse as usual
    if (root.isDirectory) {
      for ((suff, fs) <- allFiles(MSet())(root) groupBy(suff) ;
           exor <- Exors.get(suff)) {
        log.info(s"Processing $suff ${fs.size}")
        fs foreach exor.process(visitor)
      }
    }
    // if the source root is a jar file, operate on its contents
    else if (root.getName.endsWith(".jar")) {
      val jin = new JarInputStream(new FileInputStream(root))
      var entry = jin.getNextJarEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val name = entry.getName
          Exors.get(suff(name)) foreach { ex =>
            ex.process(visitor, root.getPath + "!" + name, new InputStreamReader(jin))
          }
        }
        entry = jin.getNextJarEntry
      }
    }
    // otherwise throw our hands up in defeat
    else log.warning(s"Unable to extract from ${root.getPath}.")
  }

  // TODO: allow further customization
  private def isSkipDir (dir :File) = SkipDirNames(dir.getName)
  private val SkipDirNames = Set(".", "..", "CVS", ".svn", ".git", ".hg")

  private def suff (file :File) :String = suff(file.getName)
  private def suff (name :String) :String = name.substring(name.lastIndexOf(".") + 1)

  private def allFiles (accum :MSet[File])(pdir :File) :MSet[File] = {
    val (dirs, files) = pdir.listFiles partition(_.isDirectory)
    accum ++= (files map(_ getCanonicalFile) toSet)
    dirs filterNot(isSkipDir) foreach allFiles(accum)
    accum
  }

  private val Exors = Map(
    "java" -> new ClikeExtractor("java"),
    "scala" -> new ClikeExtractor("scala"),
    "cs" -> new ClikeExtractor("cs"),
    "as" -> new ClikeExtractor("as")
  )
}

trait Extractor {
  def process (visitor :Visitor)(file :File) {
    process(visitor, file.getPath, new FileReader(file))
  }

  def process (visitor :Visitor, unitName :String, reader :Reader)
}
