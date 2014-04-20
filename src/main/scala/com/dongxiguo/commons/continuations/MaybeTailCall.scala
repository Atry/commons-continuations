package com.dongxiguo.commons.continuations
import scala.util.continuations._
import scala.util.control.TailCalls
import scala.language.higherKinds
import java.util.concurrent.atomic.AtomicInteger
import com.dongxiguo.fastring.Fastring.Implicits._
sealed trait MaybeTailCalls[TailRec[+A]] {

  final val doneUnit: TailRec[Unit] = this.done()

  def done[A](result: A): TailRec[A]

  def tailcall[A](rest: => TailRec[A]): TailRec[A]

  def result[A](tailRec: TailRec[A]): A
}

object MaybeTailCalls {

  implicit private val (logger, formatter, appender) = ZeroLoggerFactory.newLogger(this)
  import formatter._

  class DebuggingTailRec[+Result](val underlying: TailCalls.TailRec[Result]) extends AnyVal

  object DebuggingTailCalls extends AtomicInteger with MaybeTailCalls[DebuggingTailRec] {

    override final def done[A](result: A): DebuggingTailRec[A] = {
      val id = this.getAndIncrement()
      logger.info(fast"Creating TailRec of done(#$id).")
      new DebuggingTailRec(TailCalls.done {
        logger.info(fast"Executing TailRec of done(#$id).")
        result
      })
    }

    override final def tailcall[A](rest: => DebuggingTailRec[A]): DebuggingTailRec[A] = {
      val id = this.getAndIncrement()
      logger.info(fast"Creating TailRec of tailcall(#$id).")
      new DebuggingTailRec(TailCalls.tailcall {
        logger.info(fast"Executing TailRec of tailcall(#$id).")
        rest.underlying
      })
    }

    override final def result[A](tailRec: DebuggingTailRec[A]): A =
      tailRec.underlying.result

  }

  implicit object EnableTailCalls extends MaybeTailCalls[TailCalls.TailRec] {

    override final def done[A](result: A): TailCalls.TailRec[A] =
      TailCalls.done(result)

    override final def tailcall[A](rest: => TailCalls.TailRec[A]): TailCalls.TailRec[A] =
      TailCalls.tailcall(rest)

    override final def result[A](tailRec: TailCalls.TailRec[A]): A =
      tailRec.result

  }

  implicit object DisableTailCalls extends MaybeTailCalls[({ type Id[+A] = A })#Id] {

    override final def done[A](result: A): A = result

    override final def tailcall[A](rest: => A): A = rest

    override final def result[A](tailRec: A): A = tailRec

  }

  final def done[TailRec[+X]]()(implicit tailCalls: MaybeTailCalls[TailRec]): TailRec[Unit] =
    tailCalls.doneUnit

  final def done[TailRec[+X], A](result: A)(implicit tailCalls: MaybeTailCalls[TailRec]): TailRec[A] =
    tailCalls.done(result)

  final def tailcall[TailRec[+X], A](rest: => TailRec[A])(implicit tailCalls: MaybeTailCalls[TailRec]): TailRec[A] =
    tailCalls.tailcall(rest)

  final def result[TailRec[+X], A](tailRec: TailRec[A])(implicit tailCalls: MaybeTailCalls[TailRec]): A =
    tailCalls.result(tailRec)

  final def hang[TailRec[+X]](implicit tailCalls: MaybeTailCalls[TailRec]): Nothing @cps[TailRec[Unit]] = {
    scala.util.continuations.shift { _: Any =>
      tailCalls.done()
    }
  }
}
// vim: et sts=2 sw=2
