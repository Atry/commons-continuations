`commons-continuations` is a collection of utilities which is designed to help Scala programmers to work with continuations.

# Features

## Allow `for` statement with a suspendable code block.

	import com.dongxiguo.commons.continuations.CollectionConverters._
	val mySeq = Seq("foo", "bar", "baz")
	for (element in mySeq.asSuspendable.par) yield {
	  shiftUnit("Result from a suspendable expression: " + element)
	}

## Hang up a continuation

	import com.dongxiguo.commons.continuations.Hang
	shift(Hang)

## Use continuation with NIO2 socket.

See https://github.com/Atry/commons-continuations/blob/master/src/main/scala/com/dongxiguo/commons/continuations/io/AsynchronousInputStream.scala and https://github.com/Atry/commons-continuations/blob/master/src/main/scala/com/dongxiguo/commons/continuations/io/SocketWritingQueue.scala

# Repository

If you use sbt, add following lines to you `build.sbt`:

	resolvers += "Atry's maven repository" at "http://atry.github.com/maven"
	
	libraryDependencies += "com.dongxiguo" %% "commons-continuations" % "0.1-SNAPSHOT"
	
	libraryDependencies <+= scalaVersion { v =>
	  compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
	}
	
	scalaVersion := "2.10.0-M2"
	
	scalacOptions += "-P:continuations:enable"

Note that `commons-continuations` requires Scala version `2.10` and Sbt version `0.12`.
