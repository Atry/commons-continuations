/*
 * Copyright 2012 杨博 (Yang Bo)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dongxiguo.commons.continuations.io

import java.nio.channels.CompletionHandler
import scala.util.control.Exception.Catcher

object ContinuationizedCompletionHandler {
  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)

  val IntegerHandler = new ContinuationizedCompletionHandler[java.lang.Integer]

  val VoidHandler = new ContinuationizedCompletionHandler[Void]

}

final class ContinuationizedCompletionHandler[A]
  extends CompletionHandler[A, (A => _, Catcher[Unit])] {
  import ContinuationizedCompletionHandler.logger
  import ContinuationizedCompletionHandler.formatter
  import ContinuationizedCompletionHandler.appender

  override final def completed(a: A, attachment: (A => _, Catcher[Unit])) {
    logger.finest("completed: begin")
    attachment._1(a)
    logger.finest("completed: end")
  }

  override final def failed(
    throwable: Throwable,
    attachment: (A => _, Catcher[Unit])) {
    val catcher = attachment._2
    if (catcher.isDefinedAt(throwable)) {
      catcher(throwable)
    } else {
      throw throwable
    }
  }

}

// vim: set ts=2 sw=2 et:
