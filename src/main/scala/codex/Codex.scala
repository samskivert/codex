//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import com.google.inject.{AbstractModule, Guice, Inject, Singleton}
import java.io.File
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import samscala.react.Signal
import sun.misc.{Signal => SSignal, SignalHandler}

import codex.http.HttpServer

/** A small collection of somewhat global stuffs. */
@Singleton class Codex @Inject() (log :Log, config :Config, httpServer :HttpServer) {

  /** A signal emitted during app startup. */
  val start = onceSignal()

  /** A signal emitted during app shutdown. */
  val shutdown = onceSignal()

  /** An executor service for great concurrency. */
  lazy val exec :ExecutorService = _esvc

  /** Queues a block of code to be run on the executor. */
  def exec (block : => Unit) {
    exec.execute(new Runnable {
      override def run = block
    })
  }

  /** Returns our main Codex metadata directory (creating it if necessary). */
  lazy val metaDir :File = {
    // TODO: use appropriate directory depending on platform (maybe not $HOME)
    val dir = new File(new File(System.getProperty("user.home")), ".codex")
    if (!dir.exists && !dir.mkdir) {
      log.warning("Unable to create Codex metadata directory: " + dir.getAbsolutePath)
      // TODO: terminate?
    }
    dir
  }

  private def run () {
    // register a signal handler to shutdown gracefully on ctrl-c
    val sigint = new SSignal("INT")
    val ohandler = SSignal.handle(sigint, new SignalHandler {
      def handle (sig :SSignal) {
        shutdown.emit()
      }
    })
    shutdown.onEmit {
      SSignal.handle(sigint, ohandler) // restore old signal handler
      try {
        httpServer.stop() // shutdown the web server
      } catch {
        // if we fail to stop the web server, then just stick a fork in ourselves
        case e :Exception => log.warning("Failed to stop HTTP server.", e) ; sys.exit(255)
      }
      _esvc.shutdown() // shutdown our executor service
    }

    httpServer.init(this) // prepare the web server for operation
    start.emit()          // init our various thingamabobs
    httpServer.start()    // start listening for requests
    log.info("Codex listening on http://localhost:" + config.httpPort)
    httpServer.join()     // wait for the web server to exit
    _esvc.awaitTermination(60, TimeUnit.SECONDS) // wait for the exec pool to drain
    log.info("Codex exiting...")
  }

  private def onceSignal () = new Signal[Unit]() {
    override def addConnection (prio :Int, listener :Unit => Unit) =
      super.addConnection(prio, listener).once()
  }

  private val _esvc = Executors.newFixedThreadPool(config.threadPoolSize)
}

/** The main entry point of the Codex app. */
object Codex {
  def main (args :Array[String]) {
    class CodexModule extends AbstractModule {
      protected def configure () {
        // nada for now
      }
    }
    Guice.createInjector(new CodexModule).getInstance(classOf[Codex]).run()
  }
}
