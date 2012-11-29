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

object FunctionQueueTest {
  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)

  import formatter._

  final def synchronizedBenchmark() {
    val sr1 = new FunctionQueue
    for (i <- 0 until 1000000) {
      sr1.synchronized {}
    }
  }

  final def postInSynchronizedBenchmark() {
    val sr1 = new FunctionQueue
    for (i <- 0 until 1000000) {
      sr1.synchronized {
        sr1.post {}
      }
    }
  }

  final def postInPostBenchmark() {
    val sr1 = new FunctionQueue
    val sr2 = new FunctionQueue
    for (i <- 0 until 1000000) {
      sr1.post {
        sr2.post {}
      }
    }
  }

  final def postBenchmark() {
    val sr1 = new FunctionQueue
    for (i <- 0 until 1000000) {
      sr1.post {}
    }
  }

  private def runBenchmark(benchmark: () => _) {
    val start = System.nanoTime()
    benchmark()
    println(System.nanoTime() - start)
  }

}

class FunctionQueueTest {
  import FunctionQueueTest._
  import formatter._

  @Test
  final def benchmark() {
    for (i <- 0 until 5) {
      System.gc()
      runBenchmark(postBenchmark _)
      runBenchmark(postInPostBenchmark _)
      runBenchmark(postInSynchronizedBenchmark _)
      runBenchmark(synchronizedBenchmark _)
      println()
    }
  }

  @Test
  def test() {
    // FIXME:
    val sr = new FunctionQueue
    sr.post {
      sr.post {
        logger.info("2")
      }
      logger.info("1")
    }
    sr.post {
      sr.post {
        logger.info("4")
      }
    }
    sr.post {
      sr.post {
        logger.info("5")
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
