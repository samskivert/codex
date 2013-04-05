//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import codex.data.FqId
import codex._

class ProjectsServlet extends AbstractServlet {

  override def process (ctx :Context) = {
    val byType = projects request(_ ids) groupBy {
      case (_, path) if (path.contains(".m2"))  => "m2"
      case (_, path) if (path.contains(".ivy")) => "ivy"
      case _ => "local"
    }
    ctx.success(Templates.tmpl("projects.tmpl"), new AnyRef {
      def locs = byType.get("local").toSeq flatMap (_ map Info.tupled)
      def m2s = byType.get("m2").toSeq flatMap(_ map Info.tupled)
      def ivies = byType.get("ivy").toSeq flatMap(_ map Info.tupled)
    })
  }

  private case class Info (id :FqId, path :String)
}
