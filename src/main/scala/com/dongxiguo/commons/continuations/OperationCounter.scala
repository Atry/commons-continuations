package com.dongxiguo.commons.continuations

import java.util.concurrent.atomic.AtomicInteger
import scala.util.continuations._
import scala.util.control.Exception.Catcher
import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicBoolean

final class OperationCounter extends AtomicInteger(0) {

  @volatile
  private var shutdownHandler: Unit => Unit = _

  @tailrec
  final def beforeOperation() {
    val n = super.get
    if (n == Int.MaxValue) {
      // integer overflow
      throw new IllegalStateException(
        s"A ShutDownable can only wait for no more than ${
          Int.MaxValue
        } operations.")
    } else if (n < 0) {
      throw new ShuttedDownException(
        s"$this is shutted down.")
    }
    if (!super.compareAndSet(n, n + 1)) {
      beforeOperation()
    }
  }

  final def afterOperation() {
    if (super.decrementAndGet() == Int.MinValue) {
      shutdownHandler()
    }
  }

  final def suspendableOperate(operation: => Unit @suspendable)(implicit catcher: Catcher[Unit]): Unit @suspendable = {
    beforeOperation()
    @volatile
    var completed = new AtomicBoolean(false)
    operation
    if (completed.compareAndSet(false, true)) {
      afterOperation()
    } else {
      SuspendableException.catchOrThrow(throw new IllegalStateException(
        "An operation cannot be finished more than once!"))
      shift(Hang)
    }
  }

  final def operate(operation: => Unit) {
    beforeOperation()
    operation
    afterOperation()
  }

  final def shutDown()(implicit catcher: Catcher[Unit]): Unit @suspendable = {
    shift {
      new Function1[Unit => Unit, Unit] {
        @tailrec
        override def apply(continue: Unit => Unit) {
          val n = OperationCounter.super.get
          if (n < 0) {
            SuspendableException.catchOrThrow(new ShuttedDownException(
              "ShutDownable.shutdown() can be invoked only once!"))
            return
          }
          shutdownHandler = continue
          if (OperationCounter.super.compareAndSet(n, n + Int.MinValue)) {
            if (n == 0) {
              continue()
            }
          } else {
            apply(continue)
          }
        }
      }
    }
  }

}