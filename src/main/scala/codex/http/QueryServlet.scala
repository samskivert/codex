//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.io.File

import codex._
import codex.data.{Loc, Kinds, Project, Projects}
import codex.extract.Import

class QueryServlet extends AbstractServlet {

  override def process (ctx :Context) = ctx.args match {
    case Seq("find", defn) =>
      val (path, _) = refPath(ctx.body)
      val ls = onProject(defn, path, _ findDefn defn)
      def format (loc :Loc) = s"match ${loc.offset} ${loc.compunit.getAbsolutePath}"
      ctx.success(matches(ls, format))

    case Seq("import", defn) =>
      val (path, _) = refPath(ctx.body)
      val ls = onProject(defn, path, _ findDefn(defn, Kinds.types, p => l => p.qualify(l)))
      def format (fqElem :String) = Import.computeInfo(new File(path), fqElem)
      ctx.success(matches(ls, format))

    case Seq("findoc", defn, rest @ _*) =>
      // TODO: URL encode the path so that it's just one element (so as to work on non-Unix
      // platforms... blah)
      val path = "/" + rest.mkString(File.separator)
      val ls = onProject(defn, path, _ findDefn(defn, Kinds.types, _ fordoc))
      // fire off a request for these projects to download their docs if they don't have 'em
      (Set() ++ ls map(_._1.projId)) foreach { pid =>
        projects request(_ forId(pid) map(_ invoke(_ checkdoc()))) }
      // and deliver the results based on whether we got 0, 1, or many matches
      ls match {
        case Seq() => errNotFound(s"Found no element $defn")
        case Seq((l, fqNm, path)) => ctx.rsp.sendRedirect(path)
        case _     => ctx.success(Templates.tmpl("docs.tmpl"),
                                  Map("name" -> defn, "matches" -> ls))
      }

    case _ => errBadRequest(s"Unknown query: ${ctx.args mkString "/"}")
  }

  private def matches[T] (ms :Iterable[T], fmt :(T => String)) =
    if (ms.isEmpty) "nomatch" else ms map fmt mkString("\n")

  private def refPath (body :String) = body.split("\n") match {
    case Array(path, offset) => (path, offset.toInt)
    case Array(path)         => (path, -1)
    case _ => errBadRequest("Request missing path and (optional) offset.")
  }

  private def onProject[R] (defn :String, path :String, f :(Project => R)) :R = {
    projects request (_ forPath path) match {
      case Some(p) => p request f
      case None => errNotFound(s"Unable to determine project for $path")
    }
  }
}
