//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

import com.google.inject.{Inject, Injector, Singleton}
import samscala.nexus.ExecNexus

/** Manages our actor-like entities. */
@Singleton class Codexus @Inject() (codex :Codex, log :Log, inj :Injector) extends ExecNexus {
  override protected val exec = codex.exec
  override protected def create[E] (clazz :Class[E]) = inj.getInstance(clazz)
  override protected def reportError (msg :String, t :Throwable) = log.warning(msg, t)
}
