//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.io.{InputStream, OutputStream, FileInputStream, File}
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.jar.{JarInputStream, JarEntry}
import javax.servlet.http.HttpServlet
import javax.servlet.http.{HttpServletRequest => HSRequest, HttpServletResponse => HSResponse}

import codex._

/** Regurgitates documentation from local artifact repository (.m2, .ivy, etc.) */
class DocServlet extends HttpServlet {

  override protected def doGet  (req :HSRequest, rsp :HSResponse) = {
    parseArgs(req) match {
      case Array("m2", groupId, artId, vers, path @_*) =>
        val jarpath = groupId.split("\\.").toSeq ++ Seq(artId, vers, s"$artId-$vers-javadoc.jar")
        sendDoc(rsp, file(m2Root, jarpath :_*), path.mkString("/"))

      case Array("ivy", groupId, artId, vers, path @_*) =>
        val jarpath = Seq(groupId, artId, "docs", s"$artId-$vers-javadoc.jar")
        sendDoc(rsp, file(ivyRoot, jarpath :_*), path.mkString("/"))

      case args =>
        sendError(rsp, HSResponse.SC_BAD_REQUEST, s"Unknown doc repository ${args mkString "/"}")
    }
  }

  private def sendError (rsp :HSResponse, code :Int, msg :String) {
    rsp.setStatus(code)
    rsp.setContentType("text/plain; charset=UTF-8")
    rsp.getOutputStream.write(msg.getBytes("UTF8"))
  }

  private def sendDoc (rsp :HSResponse, jar :File, path :String) {
    if (!jar.exists) {
      sendError(rsp, HSResponse.SC_NOT_FOUND, s"Doc jar missing: ${jar.getPath}")

    } else {
      def findEntry (jin :JarInputStream) :Option[JarInputStream] = {
        val jentry = jin.getNextJarEntry
        if (jentry == null) None
        else if (jentry.getName == path) Some(jin)
        else findEntry(jin)
      }

      val jin = new JarInputStream(new FileInputStream(jar))
      val entry = try findEntry(jin) catch {
        case e :Throwable =>
          sendError(rsp, HSResponse.SC_INTERNAL_SERVER_ERROR, s"Error reading $jar: $e")
          jin.close
          None
      }
      entry match {
        case None => sendError(rsp, HSResponse.SC_NOT_FOUND, s"Doc jar does not contain: $path")
        case Some(jin) =>
          rsp.setContentType(getServletContext.getMimeType(path))
          val buf = ByteBuffer.allocate(4*1024)
          val source = Channels.newChannel(jin)
          val dest = Channels.newChannel(rsp.getOutputStream)
          while (source.read(buf) != -1) {
            buf.flip()
            while (buf.hasRemaining()) dest.write(buf)
            buf.clear()
          }
          source.close()
      }
    }
  }

  private def parseArgs (req :HSRequest) = {
    def deNull (s :String) = if (s == null) "" else s
    (deNull(req.getServletPath) + deNull(req.getPathInfo)) match {
      case ""   => Array[String]()
      case info => info.dropWhile(_ == '/').split("/").dropWhile(_ == "")
    }
  }

  private val m2Root = file(home, ".m2", "repository")
  private val ivyRoot = file(home, ".ivy2", "cache")
}
