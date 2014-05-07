/*
 * Copyright 2012-2014 杨博 (Yang Bo)
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
name := "commons-continuations"

organization := "com.dongxiguo"

organizationHomepage := None

libraryDependencies <++= scalaBinaryVersion { bv =>
  bv match {
    case "2.10" => {
      Seq()
    }
    case _ => {
      Seq("org.scala-lang.plugins" % s"scala-continuations-library_$bv" % "1.0.1")
    }
  }
}

libraryDependencies <+= scalaVersion { sv =>
  "org.scala-lang" % "scala-reflect" % sv
}

libraryDependencies +=
  "com.novocode" % "junit-interface" % "0.7" % "test->default"

libraryDependencies <+= scalaVersion { sv =>
  if (sv.startsWith("2.10.")) {
    compilerPlugin("org.scala-lang.plugins" % "continuations" % sv)
  } else {
    compilerPlugin("org.scala-lang.plugins" % s"scala-continuations-plugin_$sv" % "1.0.1")
  }
}

autoCompilerPlugins := true

scalacOptions += "-P:continuations:enable"

scalacOptions += "-feature"

scalacOptions += "-unchecked"

scalacOptions += "-deprecation"

scalacOptions ++= Seq("-Xelide-below", "FINEST")

crossScalaVersions := Seq("2.10.4", "2.11.0")

libraryDependencies += "com.dongxiguo" %% "zero-log" % "0.3.5"

libraryDependencies += "com.dongxiguo.zero-log" %% "context" % "0.3.5"

version := "0.2.2-SNAPSHOT"

publishTo <<= (isSnapshot) { isSnapshot: Boolean =>
  if (isSnapshot)
    Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") 
  else
    Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
}

licenses := Seq(
  "Apache License, Version 2.0" ->
  url("http://www.apache.org/licenses/LICENSE-2.0.html"))

homepage := Some(url("https://github.com/Atry/commons-continuations"))

pomExtra <<= scalaVersion { scalaVersion =>
  <scm>
    <url>https://github.com/Atry/commons-continuations</url>
    <connection>scm:git:git://github.com/Atry/commons-continuations.git</connection>
  </scm>
  <developers>
    <developer>
      <id>Atry</id>
      <name>杨博</name>
    </developer>
  </developers>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <scala.version>{scalaVersion}</scala.version>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.scala-tools</groupId>
        <artifactId>maven-scala-plugin</artifactId>
        <version>2.15.2</version>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <compilerPlugins>
            <compilerPlugin>
              <groupId>org.scala-lang.plugins</groupId>
              <artifactId>continuations</artifactId>
              <version>$&#x7b;scala.version&#x7d;</version>
            </compilerPlugin>
          </compilerPlugins>
          <recompileMode>modified-only</recompileMode>
          <args>
            <arg>-Xelide-below</arg>
            <arg>FINEST</arg>
            <arg>-deprecation</arg>
            <arg>-unchecked</arg>
            <arg>-P:continuations:enable</arg>
          </args>
        </configuration>
      </plugin>
    </plugins>
  </build>
}

// vim: expandtab shiftwidth=2 softtabstop=2 syntax=scala
