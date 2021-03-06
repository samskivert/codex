//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import java.io.File

/** Encapsulates the location of a definition in a particular project. */
case class Loc (
  /** The id of the project that contains this definition. */
  projId :FqId,
  /** The element id of the definition. */
  elemId :Int,
  /** The simple name of the definition. */
  name :String,
  /** The kind of the definition. */
  kind :String,
  /** The full path to this loc, thorugh its owning definitions. */
  path :Seq[String],
  /** The compilation unit that contains this definition. */
  compunit :File,
  /** The character offset in `compunit` at which this definition begins. */
  offset :Int
) {
  /** The qualified name of this Loc (i.e. its path combined with dots). */
  lazy val qualName :String = path.mkString(".")

  /** Returns true if this Loc's qualified name starts with an prefix in `prefs`. */
  def startsWith (prefs :Seq[String]) = prefs exists(pref => qualName startsWith pref)

  /** Returns true if this Loc's qualified name contains any string in `strs`. */
  def contains (strs :Seq[String]) = strs exists(str => qualName contains str)
}
