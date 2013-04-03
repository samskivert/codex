//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import codex.data.FqId
import codex._

class ProjectsServlet extends HtmlServlet {

  override def title (ctx :Context) = "Projects"

  override def process (ctx :Context) = {
    val byType = projects request(_ ids) groupBy {
      case (_, path) if (path.contains(".m2"))  => "m2"
      case (_, path) if (path.contains(".ivy")) => "ivy"
      case _ => "local"
    }
    val locs = byType.get("local").toSeq flatMap (_ map locLi.tupled)
    val m2s = byType.get("m2").toSeq flatMap(_ map artLi.tupled)
    val ivies = byType.get("ivy").toSeq flatMap(_ map artLi.tupled)
    <body>
      <p>Local Projects:</p>
      <ul>{locs}</ul>
      <p>Maven Projects:</p>
      <ul>{m2s}</ul>
      <p>Ivy Projects:</p>
      <ul>{ivies}</ul>
    </body>
  }

  private def href (id :FqId) :xml.Node = href(
    id.artifactId, "/project/", id.groupId, id.artifactId, id.version)

  private val locLi = (id :FqId, path :String) => <li>{href(id)} {id.version} - {path}</li>
  private val artLi = (id :FqId, path :String) => <li>{href(id)} : {id.groupId} : {id.version}</li>
}
