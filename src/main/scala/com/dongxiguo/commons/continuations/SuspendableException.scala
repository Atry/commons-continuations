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

package com.dongxiguo.commons.continuations

import scala.util.continuations._
import scala.util.control.Exception.Catcher

object SuspendableException {
  def tryCatch[A](body: => A)(implicit catcher: Catcher[Unit]): A @suspendable =
    shift { (continue: A => Unit) =>
      try {
        continue(body)
      } catch {
        case e if catcher.isDefinedAt(e) =>
          catcher(e)
      }
    }

}

// vim: set ts=2 sw=2 et:
