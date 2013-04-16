//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

/** Defines a fully qualified project id. This is rooted in the Maven style and contains:
  * `groupId`, `artifactId` and `version`.
  */
case class FqId (groupId :String, artifactId :String, version :String) {

  /** Creates a copy of this FqId with `suff` tacked onto its version. */
  def dedupe (suff :Int) = copy(version = s"$version-$suff")

  /** Returns a path fragment that's useful for embedding this id in a URL. */
  def path = s"${groupId}/${artifactId}/${version}"

  override def toString = groupId + ":" + artifactId + ":" + version
}
