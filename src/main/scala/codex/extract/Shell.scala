//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import scala.sys.process._

object Shell {

  /** Invokes the supplied command in the user's preferred shell. */
  def shell (cwd :File, args :String*) = Process(Seq(_shell, "-c", args.mkString(" ")), cwd)

  private val _shell = Option(System.getenv("SHELL")) getOrElse("sh")
}
