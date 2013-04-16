//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.text.DateFormat
import java.util.Date
import scala.collection.mutable.ListBuffer

import codex._
import codex.data.{Depend, FqId}

class ProjectServlet extends AbstractServlet {

  override def process (ctx :Context) = ctx.args match {
    case Seq(gid, aid, vers, "delete") =>
      val fqId = FqId(gid, aid, vers)
      projects invoke(_ delete(fqId))
      ctx.rsp.sendRedirect("/projects")

    case Seq(gid, aid, vers, rest @ _*) =>
      val fqId = FqId(gid, aid, vers)
      val data = projects request(_ forId(fqId)) match {
        case None     => errNotFound(s"Unknown project $fqId")
        case Some(ph) => ph request { p =>
          // force a reindex if requested
          if (rest contains "reindex") p.reindex()
          // also (re)index if the project has never been indexed
          else p.triggerFirstIndex()

          val unitBuf = ListBuffer[Map[String,AnyRef]]()
          val elems = ListBuffer[String]()
          var curPath = ""
          def flush () = if (!elems.isEmpty) {
            unitBuf += Map("path" -> curPath, "elems" -> elems.toSeq)
            elems.clear()
          }
          p.visit(new p.Viz {
            def onCompUnit (id :Int, path :String) { flush(); curPath = path }
            def onElement (id :Int, ownerId :Int, name :String, kind :String,
                           unitId :Int, offset :Int) { elems += name }
          })
          flush()

          // get the FqIds of all this project's family members
          val family = for {
            fh <- projects request(ps => p.family map(ps.forRoot) flatten)
          } yield fh request(_.fqId)

          Map("title"   -> s"$gid - $aid - $vers",
              "id"      -> p.id,
              "proj"    -> p.fqId,
              "flavor"  -> p.flavor,
              "indexed" -> _fmt.format(new Date(p.lastIndexed)),
              "family"  -> family,
              "depends" -> p.depends,
              "units"   -> unitBuf)
        }
      }
      ctx.success(Templates.tmpl("project.tmpl"), data)

    case _ => errBadRequest(s"Invalid project id: ${ctx.args mkString "/"}")
  }

  private val _fmt = DateFormat.getDateTimeInstance
}
