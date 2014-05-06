//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.channels.Channels
import scala.io.Source

import samscala.nexus.Entity

import codex._

class Downloader extends Entity {

  def download (url :URL, into :File) {
    log.info(s"Downloading $url...")
    val uconn = url.openConnection.asInstanceOf[HttpURLConnection]
    uconn.getResponseCode match {
      case 200 =>
        val out = new FileOutputStream(into).getChannel
        try {
          val in = Channels.newChannel(uconn.getInputStream)
          try out.transferFrom(in, 0, Long.MaxValue)
          finally in.close()
        } finally out.close()
      case code =>
        log.info(s"Download failed: $code")
        Source.fromInputStream(uconn.getErrorStream).getLines foreach(l => log.info(l))
    }
  }
}
