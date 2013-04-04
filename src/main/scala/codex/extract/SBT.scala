//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import scala.collection.mutable.{Map => MMap}

import codex._
import codex.data.Depend

/** Utilities for interacting with SBT. */
object SBT {
  import scala.sys.process._

  /** Extracts settings named in `props` from an SBT project in `cwd`. */
  def extractProps (cwd :File, props :String*) :Map[String,String] = {
    // TODO: this is a fragile hack
    def trimfo (line :String) = line.substring(line.indexOf(" ")+1)
    val lines = Process(Seq("sbt", "-no-colors") ++ props, cwd).lines takeRight(
      props.size) map(trimfo)
    (Map[String,String]() /: (props zip lines))(_ + _)
  }

  /** Parses the SBT output for `library-dependencies` into Codex dependencies. */
  def parseDeps (deps :String) :Seq[Depend] =
    (deps split(", ") map(stripPrePost) map(toDep) flatten).toSeq

  /** Invokes `sbt doc` in `root` with the aim of generating javadocs. */
  def buildDocs (root :File) {
    Process(Seq("sbt", "doc"), root) !
  }

  private def stripPrePost (s :String) = s.indexOf(")") match {
    case -1 => s.substring(s.indexOf("(")+1)
    case n  => s.substring(0, n)
  }

  private def toDep (ivyDep :String) = ivyDep.split(":") match {
    case Array(org, art, vers) =>
      Some(Depend(org, art, vers, "ivy", false))
    case Array(org, art, vers, config) =>
      Some(Depend(org, art, vers, "ivy", config contains "test"))
    case _ =>
      log.warning(s"Can't parse depend: $ivyDep") ; None
  }
}
