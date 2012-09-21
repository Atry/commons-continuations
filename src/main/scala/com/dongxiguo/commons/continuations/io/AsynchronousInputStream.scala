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
package io

import scala.util.control.Exception.Catcher
import java.nio.channels._
import scala.annotation.tailrec
import scala.util.continuations._
import java.io.InputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object AsynchronousInputStream {
  private val (logger, formatter) = ZeroLoggerFactory.newLogger(this)
  import formatter._

  private[AsynchronousInputStream] final class ReadHandler(
    implicit catcher: Catcher[Unit]) extends CompletionHandler[
      java.lang.Integer,
      Function1[Int, Unit] ] {
    override final def completed(
      bytesRead:java.lang.Integer,
      continue: Function1[Int, Unit] ) {
      try {
        continue(bytesRead.intValue)
      } catch {
        case e =>
          logger.severe(
            "Exception is thrown in continuation when handling a completed asynchronous reading.",
            e)
      }
    }

    override final def failed(
      throwable:Throwable,
      continue: Function1[Int, Unit] ) {
        if (catcher.isDefinedAt(throwable)) {
          try {
            catcher(throwable)
          } catch {
            case e =>
              logger.severe(
                "Exception is thrown in continuation when handling a failed asynchronous reading.",
                e)
          }
        } else {
          logger.severe("Cannot handling a failed asynchronous reading.", throwable)
        }
    }

  }
  
  /**
   * 每次读取最少要准备多大的缓冲区
   */
  private final val MinBufferSizePerRead = 1492

  final def newTimeoutInputStream(
    channel: AsynchronousSocketChannel,
    newByteBuffer: () => ByteBuffer,
    timeout: Long = DefaultReadTimeout,
    timeoutUnit: TimeUnit = DefaultReadTimeoutUnit) =
    new AsynchronousInputStream(channel, newByteBuffer) {
      override protected def readChannel(dst: ByteBuffer)(
        implicit catcher: Catcher[Unit]):Int @suspendable = {
        shift { (continue: Function1[Int, Unit] ) =>
          try {
            channel.read(dst, timeout, timeoutUnit, continue, new ReadHandler)
          } catch {
            case e if catcher.isDefinedAt(e) => catcher(e)
          }
        }
      }

    }
}

class AsynchronousInputStream(
  channel: AsynchronousSocketChannel,
  newByteBuffer: () => ByteBuffer) extends InputStream {
  // TODO: 用软引用实现缓冲池

  import AsynchronousInputStream._

  private val buffers = new collection.mutable.Queue[ByteBuffer]

  override final def available: Int = _available

  final def capacity = buffers.foldLeft(0) { _ + _.remaining}

  private var _available:Int = _

  override final def read():Int = {
    if (_available > 0) {
      if (buffers.isEmpty) {
        -1
      } else {
        val buffer = buffers.front
        val result = buffer.get()
        if (buffer.remaining == 0) {
          buffers.dequeue()
        }
        _available -= 1
        result
      }
    } else {
      -1
    }
  }

  @tailrec
  private def read(b:Array[Byte], off:Int, len:Int, count:Int):Int = {
    if (_available <= 0 || buffers.isEmpty) {
      if (count == 0) {
        if (len == 0) {
          0
        } else {
          -1
        }
      } else {
        count
      }
    } else if (len == 0) {
      count
    } else {
      val l = math.min(len, _available)
      val buffer = buffers.front
      val remaining = buffer.remaining
      if (remaining > l) {
        buffer.get(b, off, l)
        _available = _available - l
        count + l
      } else {
        buffer.get(b, off, remaining)
        _available = _available - remaining
        buffers.dequeue()
        read(b, off + remaining, l - remaining, count + remaining)
      }
    }
  }

  /**
   * 当 prepare 未完毕时调用read将导致未定义行为。
   */
  override final def read(b:Array[Byte], off:Int, len:Int):Int =
    read(b, off, len, 0)

  /**
   * Close the associated channel.
   */
  override final def close() {
    channel.close()
  }

  protected def readChannel(dst: ByteBuffer)(
    implicit catcher:Catcher[Unit]): Int @suspendable = {
    shift { (continue: Function1[Int, Unit] ) =>
      try {
        channel.read(dst, continue, new ReadHandler)
      } catch {
        case e if catcher.isDefinedAt(e) => catcher(e)
      }
    }
  }

  private def externalReadOnce(minRead:Int)(
    implicit catcher:Catcher[Unit]):Int @suspendable = {
    if (buffers.nonEmpty) {
      val last = buffers.last
      if (last.capacity - last.limit >= math.min(minRead, MinBufferSizePerRead)) {
        last.mark()
        last.position(last.limit)
        last.limit(last.capacity)
        val bytesRead = readChannel(last)
        last.limit(last.position)
        last.reset()
        bytesRead
      } else {
        // TODO: 同时读到两个缓冲区上
        val newBuffer = newByteBuffer()
        val bytesRead = readChannel(newBuffer)
        newBuffer.flip()
        buffers.enqueue(newBuffer)
        bytesRead
      }
    } else {
      val newBuffer = newByteBuffer()
      val bytesRead = readChannel(newBuffer)
      newBuffer.flip()
      buffers.enqueue(newBuffer)
      bytesRead
    }
  }

  // TODO: 在Scala修复bug后改为do...while循环
  private def externalRead(bytesToRead: Int)(
    implicit catcher:Catcher[Unit]): Unit @suspendable = {
    val n = externalReadOnce(bytesToRead)
    if (n >= 0 && n < bytesToRead) {
      externalRead(bytesToRead - n)
    }
  }

  /**
   * Workaround to enable <code>asynchronousInputStream.available = 123</code> syntax.
   */
  @inline final def available(implicit dummy: DummyImplicit): Int = available()

  /**
   * 准备若干个字节的数据。
   * @throws java.io.EOFException 如果对面已经断开连接，会触发本异常
   */
  final def available_=(bytesRequired: Int)(
    implicit catcher:Catcher[Unit]): Unit @suspendable = {
    val c = capacity
    if (bytesRequired > c) {
      externalRead(bytesRequired - c) {
        case e if catcher.isDefinedAt(e) =>
          _available = math.min(bytesRequired, capacity)
          catcher(e)
        case e =>
          _available = math.min(bytesRequired, capacity)
          throw e
      }
      val newCapacity = capacity
      if (bytesRequired > newCapacity) {
        _available = newCapacity
        SuspendableException.catchOrThrow(new EOFException)
        shift(Hang)
      } else {
        _available = bytesRequired
      }
    } else {
      _available = bytesRequired
    }

  }

  /**
   * 准备若干个字节的数据。
   * 当本continuation执行完毕时，
   * 除非对面已经断开连接，否则保证#available一定增加到<code>bytesRequired</code>.
   */
  @deprecated(message="请改用available_=", since="0.2.0")
  final def prepare(bytesRequired:Int)(
    implicit catcher:Catcher[Unit]):Unit @suspendable = {
    val c = capacity
    if (bytesRequired > c) {
      externalRead(bytesRequired - c) {
        case e =>
          _available = math.min(bytesRequired, capacity)
          SuspendableException.catchOrThrow(e)
      }
      _available = math.min(bytesRequired, capacity)
    } else {
      _available = bytesRequired
    }
  }
}
// vim: et sts=2 sw=2
