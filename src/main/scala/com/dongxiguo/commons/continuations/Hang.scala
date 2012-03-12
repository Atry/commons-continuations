package com.dongxiguo.commons.continuations

/**
 * 挂起当前Continuation.
 * @example shift(Hang)
 */
object Hang extends ((Nothing => Unit) => Unit) {
  override def apply(neverContinue: Nothing => Unit) {}
}

// vim: set ts=2 sw=2 et:
