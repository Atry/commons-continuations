package com.dongxiguo.commons.continuations
import scala.annotation.tailrec
import scala.util.continuations._
import scala.collection.immutable.Queue

final class SequentialFunctionRunner
extends SequentialRunner[() => Any @suspendable, Queue[() => Any @suspendable]] {
  
  type Task = () => Any @suspendable

  override protected final def consumeSome(
    tasks: Queue[Task]): Queue[Task] @suspendable = {
    @volatile var current = tasks
    while (current.nonEmpty) {
      current.head.apply()
      current = current.tail
    }
    current
  }

  override protected final def taskQueueCanBuildFrom = Queue.canBuildFrom

  /**
   * 向队列加入一个任务，并尽快返回。
   */
  final def post[U](task: => U @suspendable) {
    enqueue(task _)
    flush()
  }

  /**
   * 向队列加入一个任务，并等待任务执行完。
   * @note 不要把`send`调用嵌套在`post`或`send`中。
   * 这样做容易导致死锁。
   * 应当在一次`send`或`post`返回后再调用下一次。
   */
  final def send[U](task: => U @suspendable): U @suspendable =
    shift { (continue: U => Unit) =>
      post {
        continue(task)
      }
    }
}
// vim: expandtab softtabstop=2 shiftwidth=2
