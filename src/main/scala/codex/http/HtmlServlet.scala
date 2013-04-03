//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import javax.servlet.http.HttpServletResponse
import scala.xml.{Node, NodeSeq, Text, XML}
import scala.xml.dtd.{DocType, SystemID}

/**
 * Handles shared bits for servlets that process request args and generate an HTML response.
 */
abstract class HtmlServlet extends AbstractServlet {

  override type RES = NodeSeq

  /** Returns the title of the HTML page generated by this servlet. */
  def title (ctx :Context) :String

  /** Performs the primary work of this service.
   * @return an XML tree wrapped in a `<body>` tag. */
  def process (ctx :Context) :NodeSeq

  override def writeOutput (rsp :HttpServletResponse, ctx :Context, result :NodeSeq) = {
    val page = (<html><head>
                <title>codex: {title(ctx)}</title>
                <link rel="stylesheet" type="text/css" href="/styles.css"/>
                </head>{result}</html>)
    rsp.setContentType("text/html; charset=UTF-8");
    XML.write(rsp.getWriter, page,
              "utf-8", false, DocType("html", SystemID("about:legacy-compat"), Nil))
  }

  // various HTML generating helper methods
  protected def href (text :Any, root :String, args :Any*) = {
    val href = root + args.mkString("/")
    <a href={href}>{text.toString}</a>
  }

  protected def div (clazz :String, body :Traversable[Node]) = <div class={clazz}>{body}</div>

  protected def header (text :String) = div("header", new Text(text))

  protected def table (rows :Traversable[Node]) = <table>{rows}</table>
  protected def toRow (cols :Seq[Any]) :Node = <tr>{cols.map(c => <td>{c.toString}</td>)}</tr>
  protected def toRow (tup :(String,String)) :Node = toRow(Seq(tup._1, tup._2))
  protected def toRow (tup :(String,String,String)) :Node = toRow(Seq(tup._1, tup._2, tup._3))
}
