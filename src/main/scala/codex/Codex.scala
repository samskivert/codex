//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import java.io.File
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import samscala.react.Signal
import sun.misc.{Signal => SSignal, SignalHandler}

import codex.http.HttpServer

/** The main entry point of the Codex app. */
object Codex {

  def main (args :Array[String]) {
    // register a signal handler to shutdown gracefully on ctrl-c
    val sigint = new SSignal("INT")
    val ohandler = SSignal.handle(sigint, new SignalHandler {
      def handle (sig :SSignal) {
        shutdownSig.emit()
      }
    })

    val httpServer = new HttpServer()
    shutdownSig.onEmit {
      SSignal.handle(sigint, ohandler) // restore old signal handler
      try {
        httpServer.stop() // shutdown the web server
      } catch {
        // if we fail to stop the web server, then just stick a fork in ourselves
        case e :Exception => log.warning("Failed to stop HTTP server.", e) ; sys.exit(255)
      }
      exec.shutdown() // shutdown our executor service
    }

    httpServer.init()  // prepare the web server for operation
    startSig.emit()    // init our various thingamabobs
    httpServer.start() // start listening for requests
    log.info("Codex listening on http://localhost:" + config.httpPort)
    httpServer.join()  // wait for the web server to exit
    exec.awaitTermination(60, TimeUnit.SECONDS) // wait for the exec pool to drain
    log.info("Codex exiting...")
  }
}
