package com.dongxiguo.commons.continuations

import scala.language.higherKinds
import scala.util.continuations._
import scala.util.control.Exception.Catcher
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import java.io.Closeable

object Reconnector {
  private[Reconnector] sealed abstract class State[+Channel <: Closeable]

  private final case class Connecting[TailRec[+X]: MaybeTailCalls, Channel <: Closeable](handlers: List[(Channel => TailRec[Unit], Catcher[Unit])]) extends State[Channel]

  private final case class Connected[+Channel <: Closeable](channel: Channel) extends State[Channel]

  private final case object Empty extends State[Nothing]

  private final case object Closed extends State[Nothing]

}

abstract class Reconnector[TailRec[+X]: MaybeTailCalls, Channel <: Closeable] extends AtomicReference[Reconnector.State[Channel]](Reconnector.Empty) with Closeable {

  private type suspendable = cps[TailRec[Unit]]

  import Reconnector._

  protected def connect(implicit catcher: Catcher[Unit]): Channel @suspendable

  @tailrec
  @throws(classOf[ShuttedDownException])
  final def invalidate() {
    get match {
      case oldValue @ Connected(channel) =>
        if (compareAndSet(oldValue, Empty)) {
          channel.close()
        } else {
          invalidate()
        }
      case Empty =>
      case Closed => throw new ShuttedDownException("Reconnector is shutted down!")
      case _: Connecting[_, _] =>
    }
  }

  @tailrec
  final def close() {
    get match {
      case oldValue @ Connected(channel) =>
        if (compareAndSet(oldValue, Closed)) {
          channel.close()
        } else {
          close()
        }
      case Empty =>
      case Closed =>
      case _: Connecting[_, _] =>
    }
  }

  @tailrec
  private def exception(e: Exception) {
    get match {
      case oldValue @ Connecting(handlers) =>
        if (compareAndSet(oldValue, Empty)) {
          for ((_, catcher) <- handlers) {
            if (catcher.isDefinedAt(e)) {
              catcher(e)
            }
          }
        } else {
          exception(e)
        }
      case Closed =>
      case _ => throw new IllegalStateException
    }

  }

  @tailrec
  private def provide(newChannel: Channel) {
    get match {
      case oldValue @ Connecting(handlers) =>
        if (compareAndSet(oldValue, Connected(newChannel))) {
          for ((handler, _) <- handlers) {
            handler.asInstanceOf[Channel => Unit](newChannel)
          }
        } else {
          provide(newChannel)
        }
      case Closed => newChannel.close()
      case _ => throw new IllegalStateException
    }
  }

  final def acquire(implicit catcher: Catcher[Unit]): Channel @suspendable = {
    shift(new ((Channel => TailRec[Unit]) => TailRec[Unit]) {

      @tailrec
      final def apply(continue: Channel => TailRec[Unit]) = {
        get match {
          case Connected(channel) =>
            //continue(channel)
            try {
              MaybeTailCalls.tailcall(continue(channel))
            } catch {
              case e if catcher.isDefinedAt(e) => {
                catcher(e)
                MaybeTailCalls.done()
              }
            }
          case oldValue @ Empty =>
            if (compareAndSet(oldValue, Connecting(List((continue, catcher))))) {
              reset0[TailRec[Unit]] {
                provide(connect {
                  case e: Exception =>
                    exception(e)
                })
                MaybeTailCalls.done()
              }
            } else {
              apply(continue)
            }
          case Closed =>
            SuspendableException.catchOrThrow(new ShuttedDownException("Reconnector is shutted down!"))
            MaybeTailCalls.done()
          case oldValue @ Connecting(handlers) =>
            if (!compareAndSet(oldValue, Connecting((continue, catcher) :: handlers.asInstanceOf[List[(Channel => TailRec[Unit], Catcher[Unit])]]))) {
              apply(continue)
            } else {
              // 挂起
              MaybeTailCalls.done()
            }
        }
      }
    })
  }
}