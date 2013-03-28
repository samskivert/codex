//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import java.io.{File, FileReader}
import java.util.Properties

import com.google.inject.{Inject, Singleton}

/** Reads config info from a properties file and makes it available to all comers. */
@Singleton class Config @Inject() (log :Log) {

  /** The port on which we listen for HTTP connections. */
  def httpPort :Int = _props.getProperty("http_port", "3003").toInt

  /** The number of threads used to grindy grindy. */
  def threadPoolSize :Int = _props.getProperty("thread_pool_size", "4").toInt

  private val _props = {
    val p = new Properties
    val f = new File("codex.properties")
    try {
      if (f.exists) p.load(new FileReader(f))
      else log.info("No codex.properties, using defaults.")
    } catch {
      case e :Exception => log.warning("Failed to read config", "file", f, e)
    }
    p
  }
}
