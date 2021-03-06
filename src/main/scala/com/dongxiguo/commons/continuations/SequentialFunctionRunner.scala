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
import scala.annotation.tailrec
import scala.util.continuations._
import scala.collection.immutable.Queue

@deprecated("应改用com.dongxiguo.commons.continuations.FunctionQueue", "0.1.2")
final class SequentialFunctionRunner
extends SequentialRunner[() => Any @suspendable, Queue[() => Any @suspendable]] {
  
  type Task = () => Any @suspendable

  override protected final def consumeSome(
    tasks: Queue[Task]): Queue[Task] @suspendable = {
    object Upvalues {
      var current = tasks
    }
    import Upvalues._
    while (current.nonEmpty) {
      current.head.apply()
      current = current.tail
    }
    current
  }

  override protected final def taskQueueCanBuildFrom = Queue.canBuildFrom

  /**
   * 向队列加入一个任务，并尽快返回。
   */
  final def post[U](task: => U @suspendable) {
    enqueue(task _)
    flush()
  }

  /**
   * 向队列加入一个任务，并等待任务执行完。
   * @note 不要把`send`调用嵌套在`post`或`send`中。
   * 这样做容易导致死锁。
   * 应当在一次`send`或`post`返回后再调用下一次。
   */
  final def send[U](task: => U @suspendable): U @suspendable =
    shift { (continue: U => Unit) =>
      post {
        continue(task)
      }
    }
}
// vim: expandtab softtabstop=2 shiftwidth=2
