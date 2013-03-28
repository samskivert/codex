//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.io.Source

import com.google.inject.{Inject, Singleton}

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, DefaultServlet, ServletHolder}

/** Customizes a Jetty server and handles HTTP requests. */
@Singleton class HttpServer @Inject() (config :Config, log :Log) extends Server(config.httpPort) {

  def init (codex :Codex) {
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
          codex.exec(codex.shutdown.emit())
        }
      }
    }), "/shutdown")
    // ctx.addServlet(new ServletHolder(new DefaultServlet), "/*")

    // // if there's another Codex running, tell it to step aside
    // try {
    //   val rsp = Source.fromURL(getServerURL("shutdown")).getLines.mkString("\n")
    //   if (!rsp.equals("byebye")) {
    //     log.warning("Got weird repsonse when shutting down existing server: " + rsp)
    //   }
    // } catch {
    //   case ce :java.net.ConnectException => // no other server, no problem!
    //   case e :Exception => log.warning("Not able to shutdown local server: " + e)
    // }
  }
}
