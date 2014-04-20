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

import scala.language.higherKinds

class FunctionQueue extends AutoConsumableQueue[() => Unit] {

  protected final def consume(task: () => Unit) {
    task()
  }

  @throws(classOf[ShuttedDownException])
  final def shutDown(task: => Unit) {
    enqueueAndShutDown(task _: () => Unit)
  }

  @throws(classOf[ShuttedDownException])
  final def post(task: => Unit) {
    enqueue(task _: () => Unit)
  }

  @deprecated("请手动使用shift和post的组合以精确控制锁的粒度", "0.1.2")
  @inline
  final def send[TailRec[+X]: MaybeTailCalls, A](): Unit @util.continuations.cps[TailRec[Unit]] = {
    util.continuations.shift { (continue: Unit => TailRec[Unit]) =>
      post {
        MaybeTailCalls.result(continue())
      }
      MaybeTailCalls.done()
    }
  }
}
