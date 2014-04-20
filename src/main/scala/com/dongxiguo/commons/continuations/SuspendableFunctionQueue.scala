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

import scala.language.higherKinds
import SuspendableFunctionQueue._
import java.util.concurrent.atomic.AtomicReference

object SuspendableFunctionQueue {
  //type Task = () => _ @suspendable

  def enquene[TailRec[+X]: MaybeTailCalls](task: () => _ @cps[TailRec[Unit]])(origin: List[() => _ @cps[TailRec[Unit]]]): List[() => _ @cps[TailRec[Unit]]] = {
    task :: origin
  }

  sealed abstract class State

  final case object Idle extends State

  final case object ShuttedDown extends State

  final case class Running[TailRec[+X]: MaybeTailCalls](tasks: List[() => _ @cps[TailRec[Unit]]]) extends State

  final case class ShuttingDown[TailRec[+X]: MaybeTailCalls](tasks: List[() => _ @cps[TailRec[Unit]]]) extends State
}

class SuspendableFunctionQueue[TailRec[+X]: MaybeTailCalls] extends AtomicReference[State](Idle) {

  private type TaskList = List[() => _ @cps[TailRec[Unit]]]

  private type suspendable = cps[TailRec[Unit]]

  private def takeMore() {
    get match {
      case old @ Running(Nil) => {
        if (!compareAndSet(old, Idle)) {
          return takeMore()
        }
      }
      case old @ Running(tasks: TaskList) => {
        if (compareAndSet(old, Running(Nil))) {
          MaybeTailCalls.result(reset[TailRec[Unit], TailRec[Unit]] {
            val i = tasks.reverseIterator
            while (i.hasNext) {
              i.next()()
            }
            takeMore()
            MaybeTailCalls.done()
          })
        } else {
          return takeMore()
        }
      }
      case old @ ShuttingDown(Nil) => {
        if (!compareAndSet(old, ShuttedDown)) {
          return takeMore()
        }
      }
      case old @ ShuttingDown(tasks: TaskList) => {
        if (compareAndSet(old, ShuttingDown(Nil))) {
          MaybeTailCalls.result(reset[TailRec[Unit], TailRec[Unit]] {
            val i = tasks.reverseIterator
            while (i.hasNext) {
              i.next()()
            }
            takeMore()
            MaybeTailCalls.done()
          })
        } else {
          return takeMore()
        }
      }
      case Idle | ShuttedDown =>
        throw new IllegalStateException
    }
  }

  @annotation.tailrec
  final def shutDown() {
    get match {
      case old @ Idle => {
        if (!compareAndSet(old, ShuttedDown)) {
          return shutDown()
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, ShuttingDown(tasks.asInstanceOf[List[() => _ @cps[TailRec[Unit]]]]))) {
          return shutDown()
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }

  @annotation.tailrec
  final def shutDown[U](task: => U @suspendable) {
    get match {
      case old @ Idle => {
        if (compareAndSet(old, ShuttingDown(Nil))) {
          MaybeTailCalls.result(reset {
            task
            takeMore()
            MaybeTailCalls.done()
          })
        } else {
          shutDown(task)
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, ShuttingDown(task _ :: tasks.asInstanceOf[List[() => _ @cps[TailRec[Unit]]]]))) {
          return shutDown(task)
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }

  @annotation.tailrec
  final def post[U](task: => U @suspendable) {
    get match {
      case old @ Idle => {
        if (compareAndSet(old, Running(Nil))) {
          MaybeTailCalls.result(reset {
            task
            takeMore()
            MaybeTailCalls.done()
          })
        } else {
          post(task)
        }
      }
      case old @ Running(tasks) => {
        if (!compareAndSet(old, Running(task _ :: tasks.asInstanceOf[List[() => _ @cps[TailRec[Unit]]]]))) {
          return post(task)
        }
      }
      case ShuttingDown(_) | ShuttedDown =>
        throw new ShuttedDownException("SequentialRunner is shutted down!")
    }
  }

  @inline
  final def send[U](task: => U @suspendable): U @suspendable = {
    util.continuations.shift { (continue: U => TailRec[Unit]) =>
      post {
        MaybeTailCalls.result(continue(task))
      }
      MaybeTailCalls.done()
    }
  }
}
