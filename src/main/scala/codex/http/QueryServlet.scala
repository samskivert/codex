//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import com.google.inject.{Inject, Singleton}

import codex.Log
import codex.data.{Loc, Project, Projects}

@Singleton class QueryServlet @Inject() (log :Log, projs :Projects) extends RPCServlet(log) {

  override def process (ctx :Context) = ctx.args match {
    case Seq("find", defn) => findDefn(defn, ctx.body) match {
      case Seq() => errNotFound("Definition not found")
      case ls    => ls map(format) mkString("\n")
    }

    case Seq("import", defn) => errInternalError("TODO")

    case _ => errBadRequest("Unknown query: " + ctx.args.mkString("/"))
  }

  private def findDefn (defn :String, body :String) :Seq[Loc] = {
    val (file, offset) = body.split("\n") match {
      case Array(file, offset) => (file, offset.toInt)
      case Array(file)         => (file, -1)
      case _ => errBadRequest("Request missing file and (optional) offset.")
    }
    val p = projs.forPath(file).getOrElse(errNotFound("Unable to determine project for " + file))
    // TODO: also search this project's dependencies for the defn
    p.findDefn(defn)
  }

  // formats a location for responses
  private def format (loc :Loc) = s"${loc.offset} ${loc.compunit.getAbsolutePath}"
}
