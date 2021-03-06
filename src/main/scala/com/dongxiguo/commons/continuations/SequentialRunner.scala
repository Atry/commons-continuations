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

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.util.continuations._
import scala.collection.TraversableLike
import com.dongxiguo.fastring.Fastring.Implicits._

@deprecated("应改用com.dongxiguo.commons.continuations.FunctionQueue", "0.1.2")
protected object SequentialRunner {
  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)

  private[SequentialRunner] sealed abstract class State[Task, TaskQueue <: TraversableLike[Task, TaskQueue]]

  private final case class Idle[Task, TaskQueue <: TraversableLike[Task, TaskQueue]](
    tasks: TaskQueue) extends State[Task, TaskQueue]

  private final case class Running[Task, TaskQueue <: TraversableLike[Task, TaskQueue]](
    tasks: TaskQueue) extends State[Task, TaskQueue]

  private final case class ShuttedDown[Task, TaskQueue <: TraversableLike[Task, TaskQueue]](
    tasks: TaskQueue) extends State[Task, TaskQueue]
}

// FIXME: SequentialRunner性能太差，enqueue和flush竟然需要3000纳秒，比一次线程切换开销还大！
@deprecated("应改用com.dongxiguo.commons.continuations.FunctionQueue", "0.1.2")
abstract class SequentialRunner[Task, TaskQueue <: TraversableLike[Task, TaskQueue]]
  extends AtomicReference[SequentialRunner.State[Task, TaskQueue]] {
  import SequentialRunner._

  protected def consumeSome(tasks: TaskQueue): TaskQueue @suspendable

  implicit protected def taskQueueCanBuildFrom: collection.generic.CanBuildFrom[TaskQueue, Task, TaskQueue]

  private def emptyTaskQueue: TaskQueue = taskQueueCanBuildFrom().result

  set(Idle(emptyTaskQueue))

  @tailrec
  private def takeMore(remainingTasks: TaskQueue): TaskQueue = {
    logger.finer {
      fast"${remainingTasks.size} remaining tasks now, takeMore."
    }
    super.get match {
      case oldState: ShuttedDown[Task, TaskQueue] =>
        logger.finer(fast"Found ${oldState.tasks.size} more tasks.")
        if (super.compareAndSet(oldState, ShuttedDown(emptyTaskQueue))) {
          val result = remainingTasks ++ oldState.tasks
          logger.finest(fast"After takeMore, there is ${result.size} tasks.")
          result
        } else {
          // retry
          takeMore(remainingTasks)
        }
      case oldState: Running[Task, TaskQueue] =>
        logger.finer(
          fast"remainingTasks.size: ${
            remainingTasks.size.toString
          }\noldState.tasks.size: ${
            oldState.tasks.size
          }\n(remainingTasks ++ oldState.tasks).size: ${
            (remainingTasks ++ oldState.tasks).size
          }")
        val result = remainingTasks ++ oldState.tasks
        val newState: State[Task, TaskQueue] =
          if (result.isEmpty) {
            Idle(emptyTaskQueue)
          } else {
            Running(emptyTaskQueue)
          }
        if (super.compareAndSet(oldState, newState)) {
          logger.finest(fast"After takeMore, there is ${result.size} tasks.")
          result
        } else {
          // retry
          takeMore(remainingTasks)
        }
      case Idle(_) =>
        throw new IllegalStateException
    }
  }

  private def run(tasks: TaskQueue): Unit @suspendable = {
    var varTasks = tasks
    while (!varTasks.isEmpty) {
      val remainingTasks = consumeSome(varTasks)
      varTasks = takeMore(remainingTasks)
    }
  }

  @tailrec
  final def enqueue(tasks: Task*) {
    val oldState = super.get
    val newState: State[Task, TaskQueue] =
      oldState match {
        case oldState: Idle[Task, TaskQueue] =>
          new Idle[Task, TaskQueue](oldState.tasks ++ tasks)
        case oldState: Running[Task, TaskQueue] =>
          Running(oldState.tasks ++ tasks)
        case _: ShuttedDown[Task, TaskQueue] =>
          throw new ShuttedDownException("SequentialRunner is shutted down!")
      }
    if (!super.compareAndSet(oldState, newState)) {
      // retry
      enqueue(tasks: _*)
    }
  }

  final def flush() {
    super.get match {
      case oldState: Idle[Task, TaskQueue] =>
        val newState = new Running[Task, TaskQueue](emptyTaskQueue)
        if (super.compareAndSet(oldState, newState)) {
          reset {
            run(oldState.tasks)
          }
        } else {
          // retry
          flush()
        }
      case _: Running[Task, TaskQueue] | _: ShuttedDown[Task, TaskQueue] =>
    }
  }

  /**
   * 标记为shutDown，不得再往队列中增加任务
   * @param lastTasks 这个队列将会最后执行的一批任务
   */
  @tailrec
  final def shutDown(lastTasks: Task*) {
    super.get match {
      case oldState: Idle[Task, TaskQueue] =>
        val newState = new ShuttedDown[Task, TaskQueue](emptyTaskQueue)
        if (super.compareAndSet(oldState, newState)) {
          reset {
            run(oldState.tasks ++ lastTasks)
          }
        } else {
          // retry
          shutDown(lastTasks: _*)
        }
      case oldState: Running[Task, TaskQueue] =>
        val newState =
          new ShuttedDown[Task, TaskQueue](oldState.tasks ++ lastTasks)
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          shutDown(lastTasks: _*)
        }
      case _: ShuttedDown[Task, TaskQueue] =>
    }
  }

  /**
   * 标记为shutDown，不得再往队列中增加任务
   */
  @tailrec
  final def shutDown() {
    super.get match {
      case oldState: Idle[Task, TaskQueue] =>
        val newState = new ShuttedDown[Task, TaskQueue](emptyTaskQueue)
        if (super.compareAndSet(oldState, newState)) {
          reset {
            run(oldState.tasks)
          }
        } else {
          // retry
          shutDown()
        }
      case oldState: Running[Task, TaskQueue] =>
        val newState = new ShuttedDown[Task, TaskQueue](oldState.tasks)
        if (!super.compareAndSet(oldState, newState)) {
          // retry
          shutDown()
        }
      case _: ShuttedDown[Task, TaskQueue] =>
    }
  }
}
// vim: expandtab softtabstop=2 shiftwidth=2
