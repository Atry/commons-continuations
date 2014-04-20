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
import scala.annotation._
import scala.util.continuations._
import scala.collection._
import java.util.concurrent.atomic._

object CollectionConverters {
  final class SequentialSuspendableIterable[TailRec[+X]: MaybeTailCalls, +A](underlying: Iterable[A]) {
    private type suspendable = cps[TailRec[Unit]]
    
    final def seq = this

    final def par = new ParallelSuspendableIterable[TailRec, A](underlying)

    final def filter(p: A => Boolean @suspendable): List[A] @suspendable = {
      val i = underlying.iterator
      val builder = List.newBuilder[A]
      while (i.hasNext) {
        val element = i.next()
        if (p(element)) {
          builder += element
        }
      }
      builder.result
    }

    final def foreach[U](f: A => U @suspendable): Unit @suspendable = {
      val i = underlying.iterator
      while (i.hasNext) {
        f(i.next)
      }
    }

    final def map[B: Manifest](f: A => B @suspendable): List[B] @suspendable = {
      val i = underlying.iterator
      val builder = List.newBuilder[B]
      while (i.hasNext) {
        val element = f(i.next())
        builder += element
      }
      builder.result
    }
  }

  final class ParallelSuspendableIterable[TailRec[+X]: MaybeTailCalls, +A](underlying: Iterable[A])
    extends Parallel {
    private type suspendable = cps[TailRec[Unit]]

    final def par = this

    final def seq = new SequentialSuspendableIterable[TailRec, A](underlying)

    final def filter(p: A => Boolean @suspendable): List[A] @suspendable =
      shift(
        new AtomicInteger(1) with ((List[A] => TailRec[Unit]) => TailRec[Unit]) {
          private val results = new AtomicReference[List[A]](Nil)

          @tailrec
          private def add(element: A) {
            val old = results.get
            if (!results.compareAndSet(old, element :: old)) {
              add(element)
            }
          }

          override final def apply(continue: List[A] => TailRec[Unit]): TailRec[Unit] = {
            for (element <- underlying) {
              super.incrementAndGet()
              MaybeTailCalls.result(reset {
                val pass = p(element)
                if (pass) {
                  add(element)
                }
                if (super.decrementAndGet() == 0) {
                  MaybeTailCalls.tailcall(continue(results.get))
                } else {
                  MaybeTailCalls.done()
                }
              })
            }
            if (super.decrementAndGet() == 0) {
              MaybeTailCalls.tailcall(continue(results.get))
            } else {
              MaybeTailCalls.done()
            }
          }
        })

    final def foreach[U](f: A => U @suspendable): Unit @suspendable =
      shift(
        new AtomicInteger(1) with ((Unit => TailRec[Unit]) => TailRec[Unit]) {
          override final def apply(continue: Unit => TailRec[Unit]): TailRec[Unit] = {
            for (element <- underlying) {
              super.incrementAndGet()
              MaybeTailCalls.result(reset {
                f(element)
                if (super.decrementAndGet() == 0) {
                  MaybeTailCalls.tailcall(continue())
                } else {
                  MaybeTailCalls.done()
                }
              })
            }
            if (super.decrementAndGet() == 0) {
              MaybeTailCalls.tailcall(continue())
            } else {
              MaybeTailCalls.done()
            }
          }
        })

    final def map[B: Manifest](f: A => B @suspendable): Array[B] @suspendable =
      if (underlying.isEmpty) {
        Array.empty[B]
      } else {
        shift(
          new AtomicInteger(underlying.size) with ((Array[B] => TailRec[Unit]) => TailRec[Unit]) {
            override final def apply(continue: Array[B] => TailRec[Unit]): TailRec[Unit] = {
              val results = new Array[B](super.get)
              for ((element, i) <- underlying.view.zipWithIndex) {
                MaybeTailCalls.result(reset {
                  val result = f(element)
                  results(i) = result
                  if (super.decrementAndGet() == 0) {
                    MaybeTailCalls.tailcall(continue(results))
                  } else {
                    MaybeTailCalls.done()
                  }
                })
              }
              MaybeTailCalls.done()
            }
          })
      }
  }

  final class AsParallelSuspendableIterable[+A](
    val underlying: Iterable[A]) extends AnyVal {
    final def asSuspendable[TailRec[+X]: MaybeTailCalls] = new SequentialSuspendableIterable[TailRec, A](underlying)
  }

  final class AsSequentialSuspendableIterable[+A](
    val underlying: Iterable[A]) extends AnyVal {
    final def asSuspendable[TailRec[+X]: MaybeTailCalls] = new SequentialSuspendableIterable[TailRec, A](underlying)
  }

  import language.implicitConversions

  implicit def iterableAsParallelSuspendableIterable[A](
    underlying: Iterable[A] with Parallel) =
    new AsParallelSuspendableIterable(underlying.seq)

  implicit def iterableAsSequentialSuspendableIterable[A](
    underlying: Iterable[A]) =
    new AsSequentialSuspendableIterable[A](underlying)

  implicit def arrayAsSequentialSuspendableIterable[A](underlying: Array[A]) =
    new AsSequentialSuspendableIterable[A](underlying)
}

// vim: set ts=2 sw=2 et:
