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
  /** The compilation unit that contains this definition. */
  compunit :File,
  /** The character offset in `compunit` at which this definition begins. */
  offset :Int
)
