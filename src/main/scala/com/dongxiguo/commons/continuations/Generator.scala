package com.dongxiguo.commons.continuations

import scala.collection.IterableLike
import scala.util.continuations._
import scala.collection.LinearSeqOptimized
import scala.collection.immutable.LinearSeq
import scala.collection.generic.GenericTraversableTemplate
import scala.collection.AbstractSeq
import scala.collection.generic.GenericCompanion
import scala.collection.generic.SeqFactory
import scala.collection.GenTraversableOnce
import scala.collection.generic.CanBuildFrom

final object Generator extends SeqFactory[Generator] {

  final class Builder[Element](
    var continuation: Function0[_ @cps[Generator[Element]]] = null) extends scala.collection.mutable.Builder[Element, Generator[Element]] {
    override final def clear() {
      continuation = null
    }

    override final def +=(element: Element): this.type = {
      if (continuation == null) {
        continuation = { () =>
          Generator.generate(element)
        }
      } else {
        val oldContinuation = continuation
        continuation = { () =>
          oldContinuation()
          Generator.generate(element)
        }

      }
      this
    }

    override final def result(): Generator[Element] = {
      continuation match {
        case null => Generator.Empty
        case c => Generator(continuation)
      }
    }
  }

  override final def newBuilder[Element] = new Builder

  private final case object Empty extends Generator[Nothing] {

    override final def isEmpty: Boolean = true

    override final def head: Nothing = {
      throw new NoSuchElementException("head of empty list")
    }

    override final def tail: Nothing = {
      throw new UnsupportedOperationException("tail of empty list")
    }

  }

  private final case class NonEmpty[+Element](
    override val head: Element,
    val continue: Unit => Generator[Element]) extends Generator[Element] {

    override final def isEmpty: Boolean = false

    override final def tail: Generator[Element] = continue()

  }

  final def apply[Element, U](f: () => U @cps[Generator[Element]]): Generator[Element] = {
    reset[Nothing, Generator[Element]] {
      f()
      shift { (continue: Nothing => Unit) =>
        Empty
      }
    }
  }

  @inline
  final def end[Element](): Nothing @cps[Generator[Element]] = {
    shift[Nothing, Unit, Empty.type] { (continue: Nothing => Unit) =>
      Empty
    }
  }

  @inline
  final def generate[Element](element: Element): Unit @cps[Generator[Element]] = {
    shift { (continue: Unit => Generator[Element]) =>
      NonEmpty(element, continue)
    }
  }
}

sealed abstract class Generator[+Element] extends Seq[Element]
  with LinearSeq[Element]
  with Product
  with GenericTraversableTemplate[Element, Generator]
  with LinearSeqOptimized[Element, Generator[Element]] {

  override final def companion = Generator

}