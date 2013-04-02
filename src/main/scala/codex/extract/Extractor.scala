//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, FileReader, Reader}

import codex._

/** Coordinates the process of extracting name information from a projects' source files. This
  * includes identifying which files are source files, what language they are, and parsing the
  * files and extracting name and kind info. The final phase is sometimes handled by the compiler
  * infrastructure for the language in question.
  */
abstract class Extractor {

  /** Recurses over the supplied project root, finding all files that match the language handled by
    * this extractor, parses the files and invokes the appropriate methods on `visitor` as it goes.
    */
  def extract (root :File, visitor :Visitor) {
    descend(visitor)(root)
  }

  /** Processes the supplied file, informing `visitor` as we go. */
  def process (file :File, visitor :Visitor) {
    visitor.onCompUnit(file)
    process(file.getName(), new FileReader(file), visitor)
  }

  /** Processes the supplied code, informing `visitor` as we go. */
  def process (unitName :String, reader :Reader, visitor :Visitor)

  /** Returns true if `file` is a source file that we can process. */
  protected def isSource (file :File) :Boolean

  /** Returns true if the directory in question should be skipped. Defaults to skipping a bunch of
    * known version-control system directories. TODO: allow further customization. */
  protected def isSkipDir (dir :File) = SkipDirNames(dir.getName)

  private def descend (visitor :Visitor)(pdir :File) {
    val (dirs, files) = pdir.listFiles partition(_.isDirectory)

    files filter(isSource) foreach { f =>
      val fpath = f.getCanonicalPath
      try process(f, visitor)
      catch {
        case e :Throwable => log.warning("Failed to parse " + fpath + ": " +e)
      }
    }

    // TODO: ignore symlinks
    dirs filterNot(isSkipDir) foreach descend(visitor)
  }

  private val SkipDirNames = Set(".", "..", "CVS", ".svn", ".git", ".hg")
}
