//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import java.io.{FileOutputStream, PrintStream}
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import samscala.react.Signal
import sun.misc.{Signal => SSignal, SignalHandler}

import codex.http.HttpServer

/** The main entry point of the Codex app. */
object Codex {

  val appdir = Option(System.getProperty("appdir")) map(file(_))

  def main (args :Array[String]) {
    // register a signal handler to shutdown gracefully on ctrl-c
    val sigint = new SSignal("INT")
    val ohandler = SSignal.handle(sigint, new SignalHandler {
      def handle (sig :SSignal) = shutdownSig.emit()
    })

    val httpServer = new HttpServer()
    shutdownSig.onEmit {
      SSignal.handle(sigint, ohandler) // restore old signal handler
      try httpServer.stop() // shutdown the web server
      catch {
        // if we fail to stop the web server, then just stick a fork in ourselves
        case e :Exception => log.warning("Failed to stop HTTP server.", e) ; sys.exit(255)
      }
      exec.shutdown() // shutdown our executor service
    }

    // if we're running as a Getdown app, wire up our system tray icon
    if (appdir.isDefined) initTray()

    // if we're running via Getdown, redirect our log output to a file
    if (appdir.isDefined) {
      val homeLogDir = file(home, "Library", "Logs")
      val logDir = if (homeLogDir.exists) homeLogDir else appdir.get

      // first delete any previous previous log file
      val olog = file(logDir, "old-codex.log")
      if (olog.exists) olog.delete

      // next rename the previous log file
      val nlog = file(logDir, "codex.log")
      if (nlog.exists) nlog.renameTo(olog)

      // and now redirect our output
      try {
        val logOut = new PrintStream(new FileOutputStream(nlog), true)
        System.setOut(logOut)
        System.setErr(logOut)
      } catch {
        case e :Exception => log.warning("Failed to open debug log", "path", nlog, "error", e)
      }
    }

    httpServer.init()  // prepare the web server for operation
    startSig.emit()    // init our various thingamabobs
    httpServer.start() // start listening for requests
    log.info("Codex listening on http://localhost:" + config.httpPort)
    httpServer.join()  // wait for the web server to exit
    exec.awaitTermination(60, TimeUnit.SECONDS) // wait for the exec pool to drain
    log.info("Codex exiting...")
    sys.exit(0) // annoyingly we have to force exit here due to AWT bullshit
  }

  def initTray () {
    import java.awt.event.{ActionEvent, ActionListener}
    import java.awt.image.BufferedImage
    import java.awt.{Desktop, MenuItem, PopupMenu, SystemTray, TrayIcon, Window}
    import java.net.URL
    import javax.imageio.ImageIO

    def newMenuItem (label :String, action : => Unit) = {
      val item = new MenuItem(label)
      item.addActionListener(new ActionListener {
        def actionPerformed (e :ActionEvent) = action
      })
      item
    }

    if (!SystemTray.isSupported) log.info("System tray not supported. No tray icon for you!")
    else {
      try {
        val popup = new PopupMenu
        popup.add(newMenuItem("Show projects...", {
          Desktop.getDesktop.browse(config.codexURL("projects").toURI)
        }))
        popup.add(newMenuItem("Quit", shutdownSig.emit()))

        val icon = ImageIO.read(getClass.getClassLoader.getResource("images/trayicon.png"))
        val size = SystemTray.getSystemTray.getTrayIconSize
        println(s"Tray size $size")
        val image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
        val gfx = image.createGraphics
        gfx.drawImage(icon, (size.width - icon.getWidth)/2,
                      (size.height - icon.getHeight)/2, null)
        gfx.dispose

        val tricon = new TrayIcon(image, "Codex", popup)
        shutdownSig onEmit { SystemTray.getSystemTray.remove(tricon) }
        SystemTray.getSystemTray.add(tricon)

      } catch {
        case e :Exception => log.warning("Failed to initialize tray icon: " + e)
      }
    }
  }
}
