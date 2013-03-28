//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import codex.model.{FqId, Project}

object Projects {

  def forPath (path :String) :Option[Project] = {
    None
  }

  def forId (fqId :FqId) :Option[Project] = {
    None
  }
}
