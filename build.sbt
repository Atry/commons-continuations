// vim: expandtab shiftwidth=2 softtabstop=2 syntax=scala

name := "commons-continuations"

organization := "com.dongxiguo"

organizationHomepage := None

libraryDependencies +=
  "com.novocode" % "junit-interface" % "0.7" % "test->default"

libraryDependencies <+= scalaVersion {v =>
  compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
}

scalacOptions += "-P:continuations:enable"
            
scalacOptions += "-unchecked"

scalacOptions += "-deprecation"

scalacOptions ++= Seq("-Xelide-below", "FINEST")

scalaVersion := "2.10.0-M2"

crossScalaVersions := Seq("2.10.0-M1", "2.10.0-M2")

libraryDependencies <+= scalaVersion { sv =>
  "com.dongxiguo" % ("zero-log_" + sv) % "0.1.1"
}

version := "0.1-SNAPSHOT"

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
