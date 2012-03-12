package com.dongxiguo.commons.continuations.io

import java.nio.channels.CompletionHandler
import scala.util.control.Exception.Catcher

object ContinuationizedCompletionHandler {
  val IntegerHandler = new ContinuationizedCompletionHandler[java.lang.Integer]

  val VoidHandler = new ContinuationizedCompletionHandler[Void]
}

final class ContinuationizedCompletionHandler[A]
extends CompletionHandler[A, (A => _, Catcher[Unit])] {
  override final def completed(
    a: A,
    attachment: (A => _, Catcher[Unit])) {
    attachment._1(a)
  }

  override final def failed(
    e: Throwable,
    attachment: (A => _, Catcher[Unit])) {
    val catcher = attachment._2
    if (catcher.isDefinedAt(e)) {
      catcher(e)
    } else {
      throw e
    }
  }

}

// vim: set ts=2 sw=2 et:
