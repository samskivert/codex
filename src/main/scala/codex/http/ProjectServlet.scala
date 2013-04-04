//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import scala.collection.mutable.ListBuffer
import scala.xml.{Node, NodeSeq}

import codex._
import codex.data.{Depend, FqId}

class ProjectServlet extends HtmlServlet {

  override def title (ctx :Context) = ctx.args match {
    case Seq(gid, aid, vers) => s"$gid - $aid - $vers"
    case _ => "Project ?"
  }

  override def process (ctx :Context) = ctx.args match {
    case Seq(gid, aid, vers) =>
      val fqId = FqId(gid, aid, vers)
      projects request(_ forId(fqId)) match {
        case None     => <p>{s"Unknown project $fqId"}</p>
        case Some(ph) => ph request { p =>
          val acc = ListBuffer[Node]()
          acc += <div><b>Depends:</b><br/>{toUL(p.depends)}</div>
          p.visit(new p.Viz {
            def onCompUnit (id :Int, path :String) {
              if (!acc.isEmpty) acc += <br/>
              acc += <b>{path}</b>
            }
            def onElement (id :Int, ownerId :Int, name :String, kind :String,
                           unitId :Int, offset :Int) {
              acc += <span> {name}</span>
            }
          })
          acc
        }
      }

    case _ => errBadRequest(s"Invalid project id: ${ctx.args mkString "/"}")
  }
}
