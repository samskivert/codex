//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex

package object http {

  /** Contains a result of type `A` or a failure string. See [[Success] and [[Error]]. */
  sealed abstract class Result[+A] {
    /** Returns whether this is a success. */
    def isSuccess :Boolean

    /** Returns our payload, only valid iff `isSuccess`. */
    def get :A

    /** Returns our error message, only valid iff `!isSuccess`. */
    def error :String

    /** Applies `f` to our payload, iff success, returning new success. Passes failure straight
      * through without calling `f`.
      */
    @inline final def map[B] (f :A => B) :Result[B] =
      if (isSuccess) success(f(get)) else this.asInstanceOf[Result[B]]

    /** Applies `f` to our payload, iff success, returning new success or failure. Passes failure
      * straigth through without calling `f`.
      */
    @inline final def flatMap[B] (f :A => Result[B]) :Result[B] =
      if (isSuccess) f(this.get) else this.asInstanceOf[Result[B]]

    /** Applies `onSuccess` to our payload, if success; applies `onFailure` to our error message if
      * not success. */
    @inline final def fold[B] (onSuccess :A => B, onError :String => B) :B =
      if (isSuccess) onSuccess(get)
      else onError(error)

    // TODO: filter and other monadic bits
  }

  /** Returns a success [[Result]]. */
  def success[A] (value :A) = new Result[A] {
    def isSuccess = true
    def get = value
    def error = throw new IllegalStateException("Not error")
  }

  /** Returns an error [[Result]]. */
  def error (msg :String) = new Result[Nothing] {
    def isSuccess = false
    def get = throw new IllegalStateException("Not success")
    def error = msg
  }
}
