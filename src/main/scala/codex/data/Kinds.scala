//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

/** Defines various groups of kinds. */
object Kinds {

  /** "Term" elements: methods, vars, vals. */
  val terms = Set("def", "var", "val")

  /** "Type" elements: classes, interfaces, etc. */
  val types = Set("interface", "@interface", "trait", "class", "enum", "object", "struct")

  /** "Module" elements: package, object, etc. */
  val modules = Set("packge", "object", "namespace")
}
