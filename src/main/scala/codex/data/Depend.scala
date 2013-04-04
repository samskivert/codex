//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

/** Models a project dependency. */
case class Depend (
  /** The group id of this depend (used for Maven-style depends). */
  groupId :String,
  /** The artifact id of this depend, generally the project name. */
  artifactId :String,
  /** The version of the project that is depended upon. */
  version :String,
  /** The flavor of this dependency (indicates from whence it came, e.g. m2, ivy, etc.) */
  flavor :String,
  /** Whether or not this is a test-only dependency. */
  forTest :Boolean
) {
  /** Returns the fqId portion of this depend. */
  def toFqId = FqId(groupId, artifactId, version)
}
