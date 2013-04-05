//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import com.samskivert.mustache.Template
import javax.servlet.http.HttpServlet
import javax.servlet.http.{HttpServletRequest => HSRequest, HttpServletResponse => HSResponse}
import scala.io.{Codec, Source}

import codex._

/** Defines a basic framework for simple servlet RPC.
  */
abstract class AbstractServlet extends HttpServlet {
  import HSResponse._

  /** An exception that can be thrown to abort processing and return an error. */
  case class HttpException (code :Int, message :String) extends Exception(message)

  /** Encapsulates the metadata with a service request. */
  trait Context {
    /** Any arguments supplied via path info. */
    def args :Seq[String]
    /** The http request. */
    def req :HSRequest
    /** The http response. */
    def rsp :HSResponse
    /** The body of the request, converted to a string. */
    def body :String

    /** Sends the supplied `text/plain` HTTP response. */
    def success (result :String) {
      rsp.setContentType("text/plain; charset=UTF-8")
      rsp.getOutputStream.write(result.getBytes("UTF8"))
    }

    /** Sends the supplied templated (`text/html`) HTTP response. */
    def success (tmpl :Template, data :AnyRef) {
      rsp.setContentType("text/html; charset=UTF-8")
      tmpl.execute(data, rsp.getWriter)
    }
  }

  /** Performs the actual work of this servlet.
    * @throws HttpException as a convenient way to abort with an error response. */
  def process (ctx :Context)

  // various error throwing convenience methods
  protected def errBadRequest     (errmsg :String) = errorC(SC_BAD_REQUEST, errmsg)
  protected def errInternalError  (errmsg :String) = errorC(SC_INTERNAL_SERVER_ERROR, errmsg)
  protected def errForbidden      (errmsg :String) = errorC(SC_FORBIDDEN, errmsg)
  protected def errNotFound       (errmsg :String) = errorC(SC_NOT_FOUND, errmsg)
  protected def errorC (code :Int, errmsg :String) = throw new HttpException(code, errmsg)

  // route gets and posts to the same place
  override protected def doGet  (req :HSRequest, rsp :HSResponse) = process(req, rsp)
  override protected def doPost (req :HSRequest, rsp :HSResponse) = process(req, rsp)

  protected def process (req :HSRequest, rsp :HSResponse) {
    var ctx :Context = null
    try {
      ctx = mkContext(req, rsp)
      process(ctx)
    } catch {
      case he :HttpException =>
        rsp.setStatus(he.code)
        rsp.setContentType("text/plain; charset=UTF-8")
        rsp.getOutputStream.write(he.getMessage.getBytes("UTF8"))
      case e :Throwable =>
        codex.log.warning("Request failure", "url", req.getRequestURI, e)
        rsp.sendError(SC_INTERNAL_SERVER_ERROR);
    }
  }

  protected class ContextImpl (
    val args :Seq[String],
    val req  :HSRequest,
    val rsp  :HSResponse
  ) extends Context {
    lazy val body = Source.fromInputStream(req.getInputStream)(reqCodec).mkString

    private def reqCodec :Codec = req.getCharacterEncoding match {
      case null => Codec.UTF8
      case enc => enc
    }
  }

  protected def mkContext (req :HSRequest, rsp :HSResponse) :Context =
    new ContextImpl(parseArgs(req), req, rsp)

  protected def parseArgs (req :HSRequest) = req.getPathInfo match {
    case null | "" => Array[String]()
    case info => info.substring(1).split("/")
  }
}

