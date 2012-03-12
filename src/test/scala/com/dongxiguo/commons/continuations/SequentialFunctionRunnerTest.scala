package com.dongxiguo.commons.continuations
import org.junit._
import java.net.InetSocketAddress
import java.util.concurrent._
import java.nio.channels._
import java.io._
import scala.util.continuations._
import scala.util.control.Exception.Catcher

object SequentialFunctionRunnerTest {
  private val (logger, formatter) = ZeroLoggerFactory.newLogger(this)
}

class SequentialFunctionRunnerTest {
  import SequentialFunctionRunnerTest.logger
  import SequentialFunctionRunnerTest.formatter._

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
