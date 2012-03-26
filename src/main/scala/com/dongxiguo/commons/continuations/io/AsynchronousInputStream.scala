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
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import scala.annotation.tailrec
import scala.util.continuations._
import java.io.InputStream
import java.nio.ByteBuffer

private object AsynchronousInputStream {

  /**
   * 每次最少读取多少字节
   */
  private final val MinRead = 1492

  private[AsynchronousInputStream] final class ContinuationedHandler(
    implicit catcher: Catcher[Unit]) extends CompletionHandler[
      java.lang.Integer,
      Function1[Int, Unit] ] {
    override final def completed(
      bytesRead:java.lang.Integer,
      continue: Function1[Int, Unit] ) {
      continue(bytesRead.intValue)
    }

    override final def failed(
      throwable:Throwable,
      continue: Function1[Int, Unit] ) {
      throwable match {
        case e if catcher.isDefinedAt(e) =>
          catcher(e)
        case e => throw e
      }
    }

  }
}

// TODO: 用软引用实现缓冲池
class AsynchronousInputStream(
  channel: AsynchronousSocketChannel,
  newByteBuffer: () => ByteBuffer) extends InputStream {
  import AsynchronousInputStream._

  private val buffers = new collection.mutable.Queue[ByteBuffer]

  override final def available: Int = limit

  private def capacity = buffers.foldLeft(0) { _ + _.remaining}

  private var limit:Int = _

  override final def read():Int = {
    if (limit > 0) {
      if (buffers.isEmpty) {
        -1
      } else {
        val buffer = buffers.front
        val result = buffer.get()
        if (buffer.remaining == 0) {
          buffers.dequeue()
        }
        limit -= 1
        result
      }
    } else {
      -1
    }
  }

  @tailrec
  private def read(b:Array[Byte], off:Int, len:Int, count:Int):Int = {
    if (limit <= 0 || buffers.isEmpty) {
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
      val l = math.min(len, limit)
      val buffer = buffers.front
      val remaining = buffer.remaining
      if (remaining > l) {
        buffer.get(b, off, l)
        limit = limit - l
        l
      } else {
        buffer.get(b, off, remaining)
        limit = limit - remaining
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


  private def readChannel(dst:ByteBuffer)(
    implicit catcher:Catcher[Unit]):Int @suspendable = {
    shift { (continue: Function1[Int, Unit] ) =>
      try {
        channel.read(dst, continue, new ContinuationedHandler)
      } catch {
        case e if catcher.isDefinedAt(e) => catcher(e)
        case e => throw e
      }
    }
  }

  private def externalReadOnce(minRead:Int)(
    implicit catcher:Catcher[Unit]):Int @suspendable = {
    if (buffers.nonEmpty) {
      val last = buffers.last
      if (last.capacity - last.limit >= math.min(minRead, MinRead)) {
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

  private def externalReadAll(bytesToRead: Int)(
    implicit catcher:Catcher[Unit]): Unit @suspendable = {
    val n = externalReadOnce(bytesToRead)(catcher)
    if (n < 0) {
      shiftUnit0[Unit, Unit]()
    } else if (n < bytesToRead) {
      externalReadAll(bytesToRead - n)
    } else {
      shiftUnit0[Unit, Unit]()
    }
  }

  final def available_=(value: Int)(
    implicit catcher:Catcher[Unit]): Unit @suspendable = {
    // TODO: 支持减少available
    // TODO: 把别的代码改成setter风格
    prepare(value)
  }

  /**
   * 准备若干个字节的数据。
   * 当本continuation执行完毕时，
   * 除非对面已经断开连接，否则保证#available一定增加到<code>bytesRequired</code>.
   */
  final def prepare(bytesRequired:Int)(
    implicit catcher:Catcher[Unit]):Unit @suspendable = {
    // TODO: 我担心prepare有性能问题，需要评测
    val c = capacity
    if (bytesRequired > c) {
      externalReadAll(bytesRequired - c)
      limit = math.min(bytesRequired, capacity)
    } else {
      limit = bytesRequired
      shiftUnit0[Unit, Unit]()
    }
  }
}
// vim: et sts=2 sw=2
