//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.{File, FileWriter, PrintWriter}
import scala.io.Source

/** A grab bag of miscellaneous crap. */
object Util {

  def intToFile (file :File, value :Int) = strToFile(file, value.toString)
  def longToFile (file :File, value :Long) = strToFile(file, value.toString)

  def fileToInt (file :File) = fileToStr(file, "0").toInt
  def fileToLong (file :File) = fileToStr(file, "0").toLong

  /** Writes `value` (a single line of text) to `file`. */
  def strToFile (file :File, value :String) {
    val out = new PrintWriter(new FileWriter(file))
    out.println(value)
    out.close
  }

  /** Reads the (single line of text) contents of `file`. Returns `defval` if the file does not exist
    * or any error occurs while reading the file. */
  def fileToStr (file :File, defval :String) = {
    if (!file.exists) defval
    else try Source.fromFile(file).getLines.next
    catch { case e :Exception => defval }
  }
}
