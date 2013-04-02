//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import codex._
import codex.data.{Loc, Project, Projects}

class QueryServlet extends RPCServlet {
  override def process (ctx :Context) = ctx.args match {
    case Seq("find", defn) =>
      val ls = findDefn(defn, ctx.body)
      if (ls.isEmpty) "nomatch" // errNotFound("Definition not found")
      else ls map format mkString("\n")

    case Seq("import", defn) => errInternalError("TODO")

    case _ => errBadRequest(s"Unknown query: ${ctx.args mkString "/"}")
  }

  private def findDefn (defn :String, body :String) = {
    val (file, offset) = body.split("\n") match {
      case Array(file, offset) => (file, offset.toInt)
      case Array(file)         => (file, -1)
      case _ => errBadRequest("Request missing file and (optional) offset.")
    }
    projects request (_ forPath file) match {
      case Some(p) => p request (_ findDefn defn)
      case None => errNotFound("Unable to determine project for $file")
    }
  }

  // formats a location for responses
  private def format (loc :Loc) = s"match ${loc.offset} ${loc.compunit.getAbsolutePath}"
}
