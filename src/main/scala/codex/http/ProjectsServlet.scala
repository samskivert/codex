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
        case (_, _, path) if (path.contains(".m2"))  => "m2"
        case (_, _, path) if (path.contains(".ivy")) => "ivy"
        case (_, _, path) if (path.endsWith(".dll")) => "dll"
        case _ => "local"
      }

      // omit local projects that share a path prefix with an existing project (they should show up
      // in the "family" of that top-level project); TODO: actually require that said projects are
      // in the top-level project's family
      val locals = byType.get("local").toSeq flatMap(_ map Info.tupled)
      val filtLocals = (Seq[Info]() /: (locals sortBy(_.path))) {
        case (Seq(), info) => Seq(info)
        case (acc,   info) => if (info.path startsWith acc.last.path) acc
                              else acc :+ info
      }

      ctx.success(Templates.tmpl("projects.tmpl"), new AnyRef {
        // TODO: sort local projects by last accessed then alphabetically?
        def locs = filtLocals sortBy(_.sortKey)
        def m2s = byType.get("m2").toSeq flatMap(_ map Info.tupled) sortBy(_.sortKey)
        def ivies = byType.get("ivy").toSeq flatMap(_ map Info.tupled) sortBy(_.sortKey)
        def dlls = byType.get("dll").toSeq flatMap(_ map Info.tupled) sortBy(_.sortKey)
      })
  }

  private case class Info (id :Long, fqId :FqId, path :String) {
    def name =
      if (fqId.artifactId endsWith "-project") fqId.artifactId dropRight "-project".length
      else if (fqId.artifactId endsWith "-parent") fqId.artifactId dropRight "-parent".length
      else fqId.artifactId
    lazy val sortKey = fqId.artifactId.toLowerCase
  }
}
