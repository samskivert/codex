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
import codex.data.FqId

/** Regurgitates documentation from local artifact repository (.m2, .ivy, etc.) */
class DocServlet extends AbstractServlet {

  override def process (ctx :Context) = ctx.args match {
    case Seq("m2", groupId, artId, vers, path @_*) =>
      val jarpath = groupId.split("\\.").toSeq ++ Seq(artId, vers, s"$artId-$vers-javadoc.jar")
      sendJarDoc(ctx.rsp, file(m2Root, jarpath :_*), path.mkString("/"))

    case Seq("ivy", groupId, artId, vers, path @_*) =>
      val jarpath = Seq(groupId, artId, "docs", s"$artId-$vers-javadoc.jar")
      sendJarDoc(ctx.rsp, file(ivyRoot, jarpath :_*), path.mkString("/"))

    case Seq("maven", groupId, artId, vers, path @_*) =>
      val root = reqProjRoot(FqId(groupId, artId, vers))
      val docpath = file(root, Seq("target", "site", "apidocs") ++ path :_*)
      sendFileDoc(ctx.rsp, docpath)

    case Seq("sbt", groupId, artId, vers, path @_*) =>
      val root = reqProjRoot(FqId(groupId, artId, vers))
      val docpath = file(root, Seq("target", "api") ++ path :_*)
      sendFileDoc(ctx.rsp, docpath)

    case _ => errBadRequest(s"Unknown doc repository ${ctx.args mkString "/"}")
  }

  private def reqProjRoot (fqId :FqId) = projects request(_ forId(fqId)) map(
    _ request(_ root)) getOrElse(errNotFound(s"Unknown project $fqId"))

  private def sendJarDoc (rsp :HSResponse, jar :File, path :String) {
    if (!jar.exists) errNotFound(s"Doc jar missing: ${jar.getPath}")

    def findEntry (jin :JarInputStream) :Option[JarInputStream] = try {
      val jentry = jin.getNextJarEntry
      if (jentry == null) None
      else if (jentry.getName == path) Some(jin)
      else findEntry(jin)
    } catch {
      case e :Throwable =>
        jin.close
        errInternalError(s"Error reading $jar: $e")
    }

    val jin = new JarInputStream(new FileInputStream(jar))
    findEntry(jin) match {
      case None => errNotFound(s"Doc jar does not contain: $path")
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

  private def sendFileDoc (rsp :HSResponse, file :File) {
    rsp.setContentType(getServletContext.getMimeType(file.getName))
    val buf = ByteBuffer.allocate(4*1024)
    val source = new FileInputStream(file).getChannel
    try {
      source.transferTo(0, Long.MaxValue, Channels.newChannel(rsp.getOutputStream))
    } finally source.close()
  }

  private val m2Root = file(home, ".m2", "repository")
  private val ivyRoot = file(home, ".ivy2", "cache")
}
