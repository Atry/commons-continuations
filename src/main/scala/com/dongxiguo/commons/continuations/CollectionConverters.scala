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

import scala.annotation._
import scala.util.continuations._
import scala.collection._
import java.util.concurrent.atomic._

object CollectionConverters {
  final class SequentialSuspendableIterable[+A](underline: Iterable[A]) {
    final def seq = this

    final def par = new ParallelSuspendableIterable(underline)

    final def filter(p: A => Boolean @suspendable): List[A] @suspendable = {
      val i = underline.iterator
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
      val i = underline.iterator
      while (i.hasNext) {
        f(i.next)
      }
    }

    final def map[B: Manifest](f: A => B @suspendable): List[B] @suspendable = {
      val i = underline.iterator
      val builder = List.newBuilder[B]
      while (i.hasNext) {
        val element = f(i.next())
        builder += element
      }
      builder.result
    }
  }

  final class ParallelSuspendableIterable[+A](underline: Iterable[A])
  extends Parallel {
    final def par = this

    final def seq = new SequentialSuspendableIterable(underline)

    final def filter(p: A => Boolean @suspendable): List[A] @suspendable =
      shift(
        new AtomicInteger(1) with ((List[A] => Unit) => Unit) {
          private val results = new AtomicReference[List[A]](Nil)

          @tailrec
          private def add(element: A) {
            val old = results.get
            if (!results.compareAndSet(old, element :: old)) {
              add(element)
            }
          }

          override final def apply(continue: List[A] => Unit) {
            for (element <- underline) {
              super.incrementAndGet()
              reset {
                val pass = p(element)
                if (pass) {
                  add(element)
                }
                if (super.decrementAndGet() == 0) {
                  continue(results.get)
                }
              }
            }
            if (super.decrementAndGet() == 0) {
              continue(results.get)
            }
          }
        })

    final def foreach[U](f: A => U @suspendable): Unit @suspendable =
      shift(
        new AtomicInteger(1) with ((Unit => Unit) => Unit) {
          override final def apply(continue: Unit => Unit) {
            for (element <- underline) {
              super.incrementAndGet()
              reset {
                f(element)
                if (super.decrementAndGet() == 0) {
                  continue()
                }
              }
            }
            if (super.decrementAndGet() == 0) {
              continue()
            }
          }
        })

    final def map[B: Manifest](f: A => B @suspendable): Array[B] @suspendable =
      shift(
        new AtomicInteger(underline.size) with ((Array[B] => Unit) => Unit) {
          override final def apply(continue: Array[B] => Unit) {
            val results = new Array[B](super.get)
            for ((element, i) <- underline.view zipWithIndex) {
              reset {
                val result = f(element)
                results(i) = result
                if (super.decrementAndGet() == 0) {
                  continue(results)
                }
              }
            }
          }
        })
  }

  final class AsParallelSuspendableIterable[+A](
    underline: Iterable[A]) {
    final def asSuspendable = new SequentialSuspendableIterable(underline)
  }

  final class AsSequentialSuspendableIterable[+A](
    underline: Iterable[A]) {
    final def asSuspendable = new SequentialSuspendableIterable(underline)
  }

  implicit def iterableAsParallelSuspendableIterable[A](
    underline: Iterable[A] with Parallel) =
    new AsParallelSuspendableIterable[A](underline.seq)

  implicit def iterableAsSequentialSuspendableIterable[A](
    underline: Iterable[A]) =
    new AsSequentialSuspendableIterable[A](underline)

  implicit def arrayAsSequentialSuspendableIterable[A](underline: Array[A]) =
    new AsSequentialSuspendableIterable[A](underline)
}


// vim: set ts=2 sw=2 et:
