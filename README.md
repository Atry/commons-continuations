**commons-continuations** is a collection of utilities which is designed to help Scala programmers to work with continuations.

# Features

## Allow `for` statement with a suspendable code block.

	import com.dongxiguo.commons.continuations.CollectionConverters._
	import scala.util.continuations.shiftUnit
	val mySeq = Seq("foo", "bar", "baz")
	val results = for (element in mySeq.asSuspendable.par) yield {
	  shiftUnit("Result from a suspendable expression: " + element)
	}

## Hang up a continuation

	import com.dongxiguo.commons.continuations.Hang
	import scala.util.continuations.shift
	shift(Hang)

## The `goto` statement

	import com.dongxiguo.commons.continuations.Label
	val repeatPoint = Label()
	doSomething()
	repeatePoint.goto() // Infinite loop

## Use continuation with NIO2 socket.

See [AsynchronousInputStream](https://github.com/Atry/commons-continuations/blob/master/src/main/scala/com/dongxiguo/commons/continuations/io/AsynchronousInputStream.scala)
and [SocketWritingQueue](https://github.com/Atry/commons-continuations/blob/master/src/main/scala/com/dongxiguo/commons/continuations/io/SocketWritingQueue.scala).

# Repository

If you use Sbt, add following lines to you `build.sbt`:
	
	libraryDependencies += "com.dongxiguo" %% "commons-continuations" % "0.2.2"

Note that `commons-continuations` requires Scala version `2.10.x` or `2.11.x`.
