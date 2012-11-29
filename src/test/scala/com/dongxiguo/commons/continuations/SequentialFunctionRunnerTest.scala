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
import org.junit._
import java.net.InetSocketAddress
import java.util.concurrent._
import java.nio.channels._
import java.io._
import scala.util.continuations._
import scala.util.control.Exception.Catcher

object SequentialFunctionRunnerTest {
  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)
}

object SynchronizedBenchmark extends testing.Benchmark {
  override final def run() {
    val sr1 = new SequentialFunctionRunner
    for (i <- 0 until 10000) {
      sr1.synchronized {}
    }
  }
}

object PostInSynchronizedBenchmark extends testing.Benchmark {
  override final def run() {
    val sr1 = new SequentialFunctionRunner
    for (i <- 0 until 10000) {
      sr1.synchronized {
        sr1.post {}
      }
    }
  }
}

object PostInPostBenchmark extends testing.Benchmark {
  override final def run() {
    val sr1 = new SequentialFunctionRunner
    val sr2 = new SequentialFunctionRunner
    for (i <- 0 until 10000) {
      sr1.post {
        sr2.post {}
      }
    }
  }
}

object PostBenchmark extends testing.Benchmark {
  override final def run() {
    val sr1 = new SequentialFunctionRunner
    for (i <- 0 until 10000) {
      sr1.post {}
    }
  }
}

class SequentialFunctionRunnerTest {
  import SequentialFunctionRunnerTest.logger
  import SequentialFunctionRunnerTest.formatter
  import SequentialFunctionRunnerTest.appender

  @Test
  def benchmark() {
    PostBenchmark.main(Array("5"))
    PostInPostBenchmark.main(Array("5"))
    SynchronizedBenchmark.main(Array("5"))
    PostInSynchronizedBenchmark.main(Array("5"))
  }

  @Test
  def test() {
    // FIXME:
    val sr = new SequentialFunctionRunner
    sr.enqueue { () =>
      sr.enqueue { () =>
        logger.info("2")
      }
      logger.info("1")
    }
    sr.enqueue { () =>
      sr.enqueue { () =>
        logger.info("4")
      }
    }
    sr.enqueue { () =>
      sr.enqueue { () =>
        logger.info("5")
      }
    }
    import language.reflectiveCalls
    assert(sr.get.asInstanceOf[{val tasks: collection.immutable.Queue[() => Any @suspendable]}].tasks.size == 3)
    sr.flush()
    assert(sr.get.asInstanceOf[{val tasks: collection.immutable.Queue[() => Any @suspendable]}].tasks.size == 0)
    sr.enqueue { () =>
      shiftUnit0[Unit, Unit]()
    }
    sr.enqueue { () =>
      shiftUnit0[Unit, Unit]()
    }
    assert(sr.get.asInstanceOf[{val tasks: collection.immutable.Queue[() => Any @suspendable]}].tasks.size == 2)
  }

}

// vim: set ts=2 sw=2 et:
