//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.{File, InputStreamReader, Reader}
import scala.sys.process.ProcessIO
import scala.sys.process.BasicIO.{input, processFully}

import codex._

/** Helper code for interacting with the `monodis` tool. */
object Monodis {

  /** Encapsulates C# assembly metadata. */
  case class AssemblyInfo (name :String, version :String)

  /** Invokes `monodis` in `cwd` with the specified args. Any output to stderr is logged.
    * @param proc a function that is passed stdout from monodis. */
  def invoke (cwd :File, args :String*)(grinder :Reader => Unit) {
    val errbuf = new StringBuffer
    val proc = Shell.shell(cwd, "monodis" +: args :_*) run new ProcessIO(
      /*stdin */ input(false),
      /*stdout*/ in => try grinder(new InputStreamReader(in)) finally in.close(),
      /*stderr*/ processFully(errbuf))
    proc.exitValue // block until process completes
    if (errbuf.length > 0) {
      log.warning("monodis failed", "args", args.mkString(" "))
      log.warning(s"stderr: $errbuf")
    }
  }

  /** Computes and returns assembly info for the specified DLL. */
  def assemblyInfo (dll :File) = {
    val lines = Shell.shell(dll.getParentFile, "monodis", "--assembly", dll.getAbsolutePath).lines
    val info = lines map(l => (l, l.indexOf(":"))) collect {
      case (l, idx) if (idx > 0) => (l.substring(0, idx).trim, l.substring(idx+1).trim)
    } toMap ;
    AssemblyInfo(info.getOrElse("Name", "unknown"), info.getOrElse("Version", "0.0.0.0"))
  }
}
