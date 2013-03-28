//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import javax.servlet.http.{HttpServletRequest => HSRequest, HttpServletResponse => HSResponse}

import codex.Log

/** A servlet that reads and response `text/plain`. */
abstract class RPCServlet (log :Log) extends AbstractServlet(log) {

  /** We deal in string results. */
  override type RES = String

  override def writeOutput (rsp :HSResponse, result :String) = {
    rsp.setContentType("text/plain; charset=UTF-8")
    rsp.getOutputStream.write(result.getBytes("UTF8"))
  }
}
