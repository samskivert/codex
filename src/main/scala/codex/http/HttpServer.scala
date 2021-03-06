//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, DefaultServlet, ServletHolder}
import scala.io.Source

import codex._

/** Customizes a Jetty server and handles HTTP requests. */
class HttpServer extends Server(config.httpPort) {

  def init () {
    // wire up our servlet context
    val ctx = new ServletContextHandler
    ctx.setContextPath("/")
    setHandler(ctx)

    // wire up our servlets
    ctx.addServlet(new ServletHolder(new HttpServlet() {
      override def doGet (req :HttpServletRequest, rsp :HttpServletResponse) {
        try {
          val out = rsp.getWriter
          out.write("byebye\n")
          out.close
        } finally {
          exec.execute(new Runnable() {
            def run = shutdownSig.emit()
          })
        }
      }
    }), "/shutdown")
    ctx.addServlet(new ServletHolder(new QueryServlet), "/query/*")
    ctx.addServlet(new ServletHolder(new ProjectsServlet), "/projects/*")
    ctx.addServlet(new ServletHolder(new ProjectServlet), "/project/*")
    ctx.addServlet(new ServletHolder(new DocServlet), "/doc/*")
    // ctx.addServlet(new ServletHolder(new DefaultServlet), "/*")

    // if there's another Codex running, tell it to step aside
    try {
      val rsp = Source.fromURL(config.codexURL("shutdown")).getLines.mkString("\n")
      if (!rsp.equals("byebye")) {
        log.warning("Got weird repsonse when shutting down existing server: " + rsp)
      }
    } catch {
      case ce :java.net.ConnectException => // no other server, no problem!
      case e :Exception => log.warning("Not able to shutdown local server: " + e)
    }
  }
}
