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

  final def blockingWait[U](body: Catcher[Unit] => U @suspendable) {
    val lock = new AnyRef
    @volatile
    var optionalResult: Option[Either[Throwable, U]] = None
    lock.synchronized {
      reset {
        val catcher: Catcher[Unit] = {
          case e: Throwable =>
            lock.synchronized {
              optionalResult match {
                case None => {
                  optionalResult = Some(Left(e))
                  lock.notify()
                }
                case _: Some[_] => {
                  throw new IllegalStateException("Cannot return or throw an Exception more than once!", e)
                }
              }
            }
        }
        val u = body(catcher)
        lock.synchronized {
          optionalResult match {
            case None => {
              optionalResult = Some(Right(u))
              lock.notify()
            }
            case some: Some[_] => {
              throw new IllegalStateException("Cannot return or throw an Exception more than once!")
            }
          }
        }
      }
      if (optionalResult == None) {
        lock.wait()
      }
    }
    val Some(result) = optionalResult
    result match {
      case Left(e) => throw e
      case Right(u) => u.asInstanceOf[U]
    }
  }

  /**
   * 使用 Catcher 回调函数处理异常。
   * @note 当`body`抛出异常时，不论`catcher`是否处理了异常，
   * `tryWithCatcher`函数都不会返回。
   * @param body 可能抛出异常的代码块
   * @param catcher 异常处理函数。
   */
  final def tryWithCatcher[A](body: => A)(
    implicit catcher: Catcher[Unit]): A @suspendable =
    shift(new ((A => Unit) => Unit) {
      override final def apply(continue: A => Unit) {
        continue(try {
          body
        } catch {
          case e if catcher.isDefinedAt(e) =>
            catcher(e)
            return
        })
      }
    })

  final def catchOrThrow(e: Throwable)(implicit catcher: Catcher[Unit]) {
    if (catcher.isDefinedAt(e)) {
      catcher(e)
    } else {
      throw e
    }
  }

}

// vim: set ts=2 sw=2 et:
