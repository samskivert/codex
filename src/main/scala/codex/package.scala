//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

import java.io.{File, FileReader}
import java.net.URL
import java.util.Properties
import java.util.concurrent.Executors
import samscala.nexus.entity
import samscala.react.Signal
import samscala.util.Log

/** A small collection of global stuffs. */
package object codex {

  /** Reads config info from a properties file and makes it available to all comers. */
  class Config {
    /** The port on which we listen for HTTP connections. */
    def httpPort :Int = _props.getProperty("http_port", "3003").toInt

    /** The number of threads used to grindy grindy. */
    def threadPoolSize :Int = _props.getProperty("thread_pool_size", "4").toInt

    /** Returns a URL for the supplied Codex resource. */
    def codexURL (path :String) = new URL(s"http://localhost:$httpPort/$path")

    private val _props = {
      val p = new Properties
      val f = file("codex.properties")
      try {
        if (f.exists) p.load(new FileReader(f))
        else log.info("No codex.properties, using defaults.")
      } catch {
        case e :Exception => log.warning("Failed to read config", "file", f, e)
      }
      p
    }
  }

  /** A signal emitted during app startup. */
  val startSig = onceSignal()

  /** A signal emitted during app shutdown. */
  val shutdownSig = onceSignal()

  /** Provides logging to all and sundry. */
  val log = new Log("codex")

  /** The user's home directory. */
  val home = file(System.getProperty("user.home"))

  /** An executor service for great concurrency. */
  implicit val exec = Executors.newFixedThreadPool(config.threadPoolSize)

  /** Provides access to our configuration. */
  lazy val config = new Config

  /** Returns our main Codex metadata directory (creating it if necessary). */
  lazy val metaDir :File = {
    // TODO: use appropriate directory depending on platform (maybe not $HOME)
    val dir = file(home, ".codex")
    if (!dir.exists && !dir.mkdir) {
      log.warning("Unable to create Codex metadata directory: " + dir.getAbsolutePath)
      // TODO: terminate?
    }
    dir
  }

  /** The entity that manages our projects. */
  val projects = entity(new data.Projects())

  /** An entity that handles downloading artifacts
    * (one at a time so as not to anger Maven Central). */
  val downloader = entity(new http.Downloader())

  /** Creates a file with `path`. */
  def file (path :String) = new File(path)

  /** Creates a new file by combining `root` with `segs` in the natural way. */
  def file (root :File, segs :String*) = (root /: segs)(new File(_, _))

  private def onceSignal () = new Signal[Unit]() {
    override def addConnection (prio :Int, listener :Unit => Unit) =
      super.addConnection(prio, listener).once()
  }
}
