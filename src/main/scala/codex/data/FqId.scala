//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

/** Defines a fully qualified project id. This is rooted in the Maven style and contains:
  * `groupId`, `artifactId` and `version`.
  */
case class FqId (groupId :String, artifactId :String, version :String) {
  override def toString = groupId + ":" + artifactId + ":" + version
}
