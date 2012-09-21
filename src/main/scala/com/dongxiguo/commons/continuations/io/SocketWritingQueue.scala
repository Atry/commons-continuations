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

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels._
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.util.continuations._

protected object SocketWritingQueue {
  private val (logger, formatter) = ZeroLoggerFactory.newLogger(this)
  import formatter._

  private[SocketWritingQueue] sealed trait State extends NotNull

  private final case class Idle(
    val buffers: List[ByteBuffer]) extends State

  private final case class Running(
    val buffers: List[ByteBuffer]) extends State

  private final case class Closing(
    val buffers: List[ByteBuffer]) extends State

  /**
   * 表示socket.close()已经调用过
   */
  private case object Closed extends State

  private case object Interrupted extends State

}

/**
 * <code>SocketWritingQueue</code>是线程安全的，允许多个线程向这里提交要写入的数据。
 * 提交的缓冲区将按提交顺序排队写入
 */
final class SocketWritingQueue(
  val socket: AsynchronousSocketChannel,
  timeout: Long = DefaultWriteTimeout,
  timeoutUnit: TimeUnit = DefaultWriteTimeoutUnit)
extends AtomicReference[SocketWritingQueue.State](SocketWritingQueue.Idle(Nil)) {
  import SocketWritingQueue.logger
  import SocketWritingQueue.formatter._

  @tailrec
  final def flush() {
    val oldState = super.get
    oldState match {
      case SocketWritingQueue.Idle(Nil) =>
      case SocketWritingQueue.Idle(buffers) =>
        if (super.compareAndSet(oldState, SocketWritingQueue.Running(Nil))) {
          startWriting(buffers.reverseIterator.toArray)
        } else {
          // retry
          flush()
        }
      case _ =>
    }
  }

  /**
   * 立即关闭SocketWritingQueue关联的socket，中断所有正在发送的数据。可以多次调用。
   */
  final def interrupt() {
    super.getAndSet(SocketWritingQueue.Interrupted) match {
      case SocketWritingQueue.Closed | SocketWritingQueue.Interrupted =>
      case _ =>
        socket.close()
        logger.fine("Socket " + socket + " is closed by interrupt().")
    }
  }

  /**
   * 关闭`SocketWritingQueue`. 
   * 如果存在正在发送的数据，当这些数据发送完时，底层套接字才会真正被关闭。
   * 如果多次调用`close()`，只有第一次调用有效，后面几次会被忽略
   */
  @tailrec
  final def close() {
    val oldState = super.get
    oldState match {
      case SocketWritingQueue.Idle(Nil) =>
        if (super.compareAndSet(oldState, SocketWritingQueue.Closed)) {
          socket.close()
          logger.fine("No data to send. Socket " + socket + " is closed.")
        } else {
          // retry
          close()
        }
      case SocketWritingQueue.Idle(buffers) =>
        val newState = SocketWritingQueue.Closing(Nil)
        if (super.compareAndSet(oldState, newState)) {
          val bufferArray = buffers.reverseIterator.toArray
          startWriting(bufferArray)
        } else {
          // retry
          close()
        }
      case SocketWritingQueue.Running(buffers) =>
        val newState = SocketWritingQueue.Closing(Nil)
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          close()
        }
      case SocketWritingQueue.Interrupted |
        SocketWritingQueue.Closing(_) |
        SocketWritingQueue.Closed =>
    }
  }

  private val writeHandler = new CompletionHandler[
    java.lang.Long,
    Function1[Long, Unit] ] {
    override final def completed(
      bytesWritten:java.lang.Long,
      continue: Function1[Long, Unit] ) {
      try {
        continue(bytesWritten.longValue)
      } catch {
        case e =>
          logger.severe(
            "Exception is thrown in continuation when handling a completed asynchronous writing.",
            e)
          sys.exit(1)
      }
    }

    override final def failed(
      throwable:Throwable,
      continue: Function1[Long, Unit] ) {
      if (throwable.isInstanceOf[IOException]) {
        interrupt()
      } else {
        logger.severe("Asynchronous writing is failed.", throwable)
        sys.exit(1)
      }
    }
  }

  
  private def writeChannel(buffers: Array[ByteBuffer]): Long @suspendable = {
    shift { (continue: Function1[Long, Unit] ) =>
      try {
        socket.write(
          buffers,
          0,
          buffers.length,
          timeout,
          timeoutUnit,
          continue,
          writeHandler)
      } catch {
        case e: IOException =>
          // 本身不处理，关闭socket通知读线程来处理
          interrupt()
      }
    }
  }

  /**
   * 当且仅当没有要发送的缓冲区时，抛出Rewind
   */
  @tailrec
  private def writeMore(remainingBuffers:Iterator[ByteBuffer]) {
    val oldState = super.get
    oldState match {
      case SocketWritingQueue.Running(Nil) =>
        if (remainingBuffers.isEmpty) {
          if (!super.compareAndSet(oldState, SocketWritingQueue.Idle(Nil))) {
            // retry
            writeMore(remainingBuffers)
          }
        } else {
          startWriting(remainingBuffers.toArray)
        }
      case SocketWritingQueue.Running(buffers) =>
        if (super.compareAndSet(oldState, SocketWritingQueue.Running(Nil))) {
          startWriting(remainingBuffers ++ buffers.reverseIterator toArray)
        } else {
          // retry
          writeMore(remainingBuffers)
        }
      case SocketWritingQueue.Closing(Nil) =>
        if (remainingBuffers.isEmpty) {
          if (super.compareAndSet(oldState, SocketWritingQueue.Closed)) {
            socket.close()
            logger.fine("Socket " + socket + " is closed after all data been sent.")
          } else {
            // retry
            writeMore(remainingBuffers)
          }
        } else {
          startWriting(remainingBuffers.toArray)
        }
      case SocketWritingQueue.Closing(buffers) =>
        if (super.compareAndSet(oldState, SocketWritingQueue.Closing(Nil))) {
          startWriting(remainingBuffers ++ buffers.reverseIterator toArray)
        } else {
          // retry
          writeMore(remainingBuffers)
        }
      case SocketWritingQueue.Idle(_) | SocketWritingQueue.Closed =>
        throw new IllegalStateException
      case SocketWritingQueue.Interrupted =>
    }
  }

  
  private def startWriting(buffers:Array[ByteBuffer]) {
    reset {
      val bytesWritten = writeChannel(buffers)
      val nextIndex = buffers indexWhere { _.hasRemaining }
      val remainingBuffers = nextIndex match {
        case -1 => Iterator.empty
        case nextIndex =>
          buffers.view(nextIndex, buffers.length).iterator
      }
      writeMore(remainingBuffers)
    }
  }

  /**
   * @throws IllegalStateException <code>SocketWritingQueue</code>已经关闭
   */
  @tailrec
  @throws(classOf[IllegalStateException])
  final def enqueue(b: ByteBuffer*) {
    val oldState = super.get
    oldState match {
      case SocketWritingQueue.Idle(buffers) =>
        val newState = SocketWritingQueue.Idle(b.foldLeft(buffers) { _.::(_) })
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          enqueue(b: _*)
        }
      case SocketWritingQueue.Running(buffers) =>
        val newState = SocketWritingQueue.Running(b.foldLeft(buffers) { _.::(_) })
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          enqueue(b: _*)
        }
      case SocketWritingQueue.Interrupted |
        SocketWritingQueue.Closing(_) |
        SocketWritingQueue.Closed =>
    }
  }

  /**
   * @throws IllegalStateException <code>SocketWritingQueue</code>已经关闭
   */
  @tailrec
  final def enqueue(buffer: ByteBuffer) {
    val oldState = super.get
    oldState match {
      case SocketWritingQueue.Idle(buffers) =>
        val newState = SocketWritingQueue.Idle(buffer :: buffers)
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          enqueue(buffer)
        }
      case SocketWritingQueue.Running(buffers) =>
        val newState = SocketWritingQueue.Running(buffer :: buffers)
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          enqueue(buffer)
        }
      case SocketWritingQueue.Interrupted |
        SocketWritingQueue.Closing(_) |
        SocketWritingQueue.Closed =>
    }
  }
}
// vim: et sts=2 sw=2
