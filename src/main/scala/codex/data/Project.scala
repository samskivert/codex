//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File
import org.squeryl.KeyedEntity
import samscala.nexus.Entity

/** The source of information about a particular project. */
class Project(
  /** The human readable name of this project. */
  val name :String,
  /** The group part of this project's fqId. */
  val groupId :String,
  /** The artifact part of this project's fqId. */
  val artifactId :String,
  /** The version string for this project. */
  val version :String,
  /** When this project was imported into the library. */
  val imported :Long,
  /** The directory at which this project is rooted, as a string. */
  val rootPath :String,
  // see lastUpdated for public API
  dbLastUpdated :Long
) extends KeyedEntity[Long] with Entity {
  def this () = this("", "", "", "", 0L, "", 0L) // for unserializing

  /** A unique identifier for this project (1 or higher). */
  val id :Long = 0L

  /** The directory at which this project is rooted. */
  val root = new File(rootPath)

  /** This project's fully qualified (aka Maven) id, or some approximation thereof. */
  lazy val fqId = FqId(groupId, artifactId, version)

  /** When this project was last updated. */
  def lastUpdated :Long = _lastUpdated

  /** Returns all definitions in this project with the specified name. */
  def findDefn (name :String) :Seq[Loc] = {
    println("TODO: findDefn " + this.name + " " + name)
    // TODO: check whether project is out of date, if so queue rescan
    // TODO: search our depends
    // TODO: also search this project's dependencies for the defn
    Seq()
  }

  override def toString = s"[id=$id, name=$name, vers=$version]"

  private var _lastUpdated = dbLastUpdated
}
