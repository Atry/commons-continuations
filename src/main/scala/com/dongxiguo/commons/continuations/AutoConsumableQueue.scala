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

import AutoConsumableQueue._
import CollectionConverters._
import java.util.concurrent.atomic.AtomicReference

object AutoConsumableQueue {
  sealed abstract class State[+Task]

  private[AutoConsumableQueue] final case object Idle extends State[Nothing]

  private[AutoConsumableQueue] final case object ShuttedDown extends State[Nothing]

  private[AutoConsumableQueue] final case class Running[+Task] private[AutoConsumableQueue] (tasks: List[Task]) extends State[Task]

  private[AutoConsumableQueue] final case class ShuttingDown[+Task] private[AutoConsumableQueue] (tasks: List[Task]) extends State[Task]
}

abstract class AutoConsumableQueue[Task] extends AtomicReference[State[Task]](Idle) {

  protected def consume(task: Task)

  protected def beforeIdle() {}

  private def takeMore() {
    get match {
      case old @ Running(Nil) => {
        beforeIdle()
        if (!compareAndSet(old, Idle)) {
          return takeMore()
        }
      }
      case old @ Running(tasks) => {
        if (compareAndSet(old, Running(Nil))) {
          tasks.reverseIterator foreach { consume(_) }
          takeMore()
        } else {
          return takeMore()
        }
      }
      case old @ ShuttingDown(Nil) => {
        if (!compareAndSet(old, ShuttedDown)) {
          return takeMore()
        }
      }
      case old @ ShuttingDown(tasks) => {
        if (compareAndSet(old, ShuttingDown(Nil))) {
          tasks.reverseIterator foreach { consume(_) }
          takeMore()
        } else {
          return takeMore()
        }
      }
      case Idle | ShuttedDown =>
        throw new IllegalStateException
    }
  }

  @throws(classOf[ShuttedDownException])
  @annotation.tailrec
  final def shutDown() {
    get match {
      case old @ Idle => {
        if (!compareAndSet(old, ShuttedDown)) {
          return shutDown()
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, ShuttingDown(tasks))) {
          return shutDown()
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }

  @throws(classOf[ShuttedDownException])
  @annotation.tailrec
  final def enqueueAndShutDown(task: Task) {
    get match {
      case old @ Idle => {
        if (compareAndSet(old, ShuttingDown(Nil))) {
          consume(task)
          takeMore()
        } else {
          enqueueAndShutDown(task)
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, ShuttingDown(task :: tasks))) {
          enqueueAndShutDown(task)
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }

  @throws(classOf[ShuttedDownException])
  @annotation.tailrec
  final def enqueue(task: Task) {
    get match {
      case old @ Idle => {
        if (compareAndSet(old, Running(Nil))) {
          consume(task)
          takeMore()
        } else {
          enqueue(task)
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, Running(task :: tasks))) {
          enqueue(task)
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }
}
