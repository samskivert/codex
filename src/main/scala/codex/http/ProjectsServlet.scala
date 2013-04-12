//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.io.File

import codex.data.FqId
import codex._

class ProjectsServlet extends AbstractServlet {

  override def process (ctx :Context) = ctx.args match {
    case Seq("import") =>
      val path = ctx.reqParam("path")
      if (!new File(path).exists) errNotFound(s"${path} is not a file or directory.")
      projects request(_ forPath(path)) match {
        case None => errNotFound("Failed to grok project. Sorry.")
        case Some(ph) =>
          ph invoke(_ reindex())
          ctx.rsp.sendRedirect("/projects")
      }

    case _ =>
      val byType = projects request(_ ids) groupBy {
        case (_, path) if (path.contains(".m2"))  => "m2"
        case (_, path) if (path.contains(".ivy")) => "ivy"
        case (_, path) if (path.endsWith(".dll")) => "dll"
        case _ => "local"
      }
      ctx.success(Templates.tmpl("projects.tmpl"), new AnyRef {
        // TODO: sort local projects by last accessed then alphabetically?
        // TODO: group local projects by family patriarch
        def locs = byType.get("local").toSeq flatMap (_ map Info.tupled) sortBy(_.id.artifactId)
        def m2s = byType.get("m2").toSeq flatMap(_ map Info.tupled) sortBy(_.id.artifactId)
        def ivies = byType.get("ivy").toSeq flatMap(_ map Info.tupled) sortBy(_.id.artifactId)
        def dlls = byType.get("dll").toSeq flatMap(_ map Info.tupled) sortBy(_.id.artifactId)
      })
  }

  private case class Info (id :FqId, path :String)
}
