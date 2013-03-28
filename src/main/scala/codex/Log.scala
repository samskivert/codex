//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import com.google.inject.Singleton

/** Wires a logger into the wild wild world of Guice. */
@Singleton class Log extends samscala.util.Log("codex")
