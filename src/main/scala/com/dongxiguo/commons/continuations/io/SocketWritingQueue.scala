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

object SocketWritingQueue {
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
final class SocketWritingQueue(val socket: AsynchronousSocketChannel)
extends AtomicReference[SocketWritingQueue.State](SocketWritingQueue.Idle(Nil)) {
  import SocketWritingQueue._
  import formatter._

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

  //private val state:AtomicReference[SocketWritingQueue.State] =
  //new AtomicReference(SocketWritingQueue.Idle(Nil))

  /**
   * 中断所有正在发送的数据。可以多次调用
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
   * 关闭SocketWritingQueue关联的socket.
   * 如果多次调用close()，只有第一次调用有效，后面几次会被忽略
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
      continue(bytesWritten.longValue)
    }

    override final def failed(
      throwable:Throwable,
      continue: Function1[Long, Unit] ) {
      throwable match {
        case e: IOException =>
          interrupt()
        case e => throw e
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
          WriteTimeOut,
          WriteTimeOutUnit,
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
