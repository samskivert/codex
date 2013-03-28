//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import com.google.inject.{Inject, Singleton}

import codex.Log
import codex.data.Projects
import codex.model.{Loc, Project}

@Singleton class QueryServlet @Inject() (log :Log) extends RPCServlet(log) {

  override def process (ctx :Context) :Result[String] = ctx.args match {
    case Seq("find", defn) => findDefn(defn, ctx.body) flatMap { ls =>
      if (ls.isEmpty) error("Definition not found")
      else            success(ls map(format) mkString("\n"))
    }

    case Seq("import", defn) => error("TODO")

    case _ => errBadRequest("Unknown query: " + ctx.args.mkString("/"))
  }

  private def findDefn (defn :String, body :String) :Result[Seq[Loc]] = {
    val (file, offset) = locFromBody(body)
    Projects.forPath(file) match {
      case None    => error("Unable to determine project for " + file)
      case Some(p) => success(Seq()) // "TODO"
    }
  }

  private def locFromBody (body :String) :(String, Int) = body.split("\n") match {
    case Array(file, offset) => (file, offset.toInt)
    case Array(file)         => (file, -1)
    case _ => errBadRequest("Request missing file and (optional) offset.")
  }

  // formats a location for responses
  private def format (loc :Loc) = s"${loc.offset} ${loc.compunit.getAbsolutePath}"
}
