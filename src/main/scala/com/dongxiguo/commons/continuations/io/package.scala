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

import java.nio._
import java.util.concurrent._
import java.nio.channels._
import scala.util.continuations._
import scala.annotation._

package object io {
  private val (logger, formatter) = ZeroLoggerFactory.newLogger(this)
  import formatter._

  private object WriteHandler
  extends CompletionHandler[java.lang.Long, Long => Unit] {
    override final def completed(
      bytesWritten: java.lang.Long,
      handler: Long => Unit) {
      try {
        handler(bytesWritten)
      } catch {
        case e =>
          logger.severe(
            "Exception is thrown in continuation when handling a completed asynchronous writing.",
            e)
      }
    }

    /**
     * 写入时不处理异常而在读取处处理异常
     */
    override final def failed(throwable: Throwable, handler:  Long => Unit) {
      logger.fine("Asynchronous operation is failed.", throwable)
      handler(-1)
    }
  }

  final val DefaultReadTimeout = 1L

  final val DefaultReadTimeoutUnit = TimeUnit.SECONDS

  final val DefaultWriteTimeout = 1L

  final val DefaultWriteTimeoutUnit = TimeUnit.SECONDS

  /**
   * 只要连接没断就能成功写入，但如果连接断开了就会失败而且不做任何提示。
   */
  final def writeAll(
    socket: AsynchronousSocketChannel,
    buffers: Array[ByteBuffer],
    bufferOffset: Int,
    bufferLength: Int,
    timeout: Long,
    unit: TimeUnit): Unit @suspendable = {
    val bytesWritten = shift { (continue: Long => Unit) =>
      socket.write(
        buffers,
        bufferOffset,
        bufferLength,
        timeout,
        unit,
        continue,
        WriteHandler)
    }
    if (bytesWritten > 0) {
      @tailrec
      def getNewOffset(i: Int): Int = {
        if (i >= bufferOffset + bufferLength) {
          i
        } else {
          val buffer = buffers(i)
          if (buffer.hasRemaining) {
            i
          } else {
            buffers(i) = null
            getNewOffset(i + 1)
          }
        }
      }
      val newOffset = getNewOffset(bufferOffset)
      val newLength = bufferLength - (newOffset - bufferOffset)
      if (newLength > 0) {
        writeAll(socket, buffers, newOffset, newLength, timeout, unit)
      }
    }
  }

  /**
   * 只要连接没断就能成功写入，但如果连接断开了就会失败而且不做任何提示。
   */
  final def writeAll(
    socket: AsynchronousSocketChannel,
    buffers: Array[ByteBuffer],
    timeout: Long = DefaultWriteTimeout,
    unit: TimeUnit = DefaultWriteTimeoutUnit): Unit @suspendable =
    writeAll(socket, buffers, 0, buffers.length, timeout, unit)
}

// vim: set ts=2 sw=2 et:
