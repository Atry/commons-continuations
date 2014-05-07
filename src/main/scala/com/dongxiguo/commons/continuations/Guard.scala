/*
 * Copyright 2012-2014 杨博 (Yang Bo)
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
import scala.collection.immutable.Queue

trait Guard[+Self] { self: Self =>

  private val queue = new FunctionQueue

  /**
   * 向队列加入一个任务，并尽快返回。
   */
  @inline
  final def post[U](task: Self => U) {
    queue.post {
      task(self)
    }
  }

  /**
   * 向队列加入一个任务，并等待任务执行完。
   * @note 不要把`send`调用嵌套在`post`或`send`中。
   * 这样做容易导致死锁。
   * 应当在一次`send`或`post`返回后再调用下一次。
   */
  @inline
  final def send[U](task: Self => U): U @suspendable = {
    shift { (continue: U => Unit) =>
      queue.post {
        continue(task(self))
      }
    }
  }
}

// vim: set ts=2 sw=2 et:
