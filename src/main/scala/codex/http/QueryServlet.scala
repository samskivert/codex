//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.io.File

import codex._
import codex.data.{Loc, Project, Projects}
import codex.extract.Import

class QueryServlet extends RPCServlet {
  override def process (ctx :Context) = ctx.args match {
    case Seq("find", defn) =>
      val (file, _) = refFile(ctx.body)
      val ls = onProject(defn, file, _ findDefn defn)
      if (ls.isEmpty) "nomatch" // errNotFound("Definition not found")
      else ls map format mkString("\n")

    case Seq("import", defn) =>
      val (file, _) = refFile(ctx.body)
      onProject(defn, file, p => {
        val ls = p findDefn defn
        if (ls.isEmpty) "nomatch"
        else Import.computeInfo(new File(file), p.qualify(ls.head.elemId))
      })

    case _ => errBadRequest(s"Unknown query: ${ctx.args mkString "/"}")
  }

  private def refFile (body :String) = body.split("\n") match {
    case Array(file, offset) => (file, offset.toInt)
    case Array(file)         => (file, -1)
    case _ => errBadRequest("Request missing file and (optional) offset.")
  }

  private def onProject[R] (defn :String, file :String, f :(Project => R)) :R = {
    projects request (_ forPath file) match {
      case Some(p) => p request f
      case None => errNotFound(s"Unable to determine project for $file")
    }
  }

  // formats a location for responses
  private def format (loc :Loc) = s"match ${loc.offset} ${loc.compunit.getAbsolutePath}"
}
