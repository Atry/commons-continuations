// vim: expandtab shiftwidth=2 softtabstop=2 syntax=scala

name := "commons-continuations"

organization := "com.dongxiguo"

libraryDependencies +=
  "com.novocode" % "junit-interface" % "0.7" % "test->default"

libraryDependencies <+= scalaVersion {v =>
  compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
}

scalacOptions += "-P:continuations:enable"
            
scalacOptions += "-unchecked"

scalacOptions += "-deprecation"

scalaVersion := "2.10.0-M2"

crossScalaVersions := Seq("2.10.0-M2")

resolvers +=
  "Atry's maven repository on github" at "http://atry.github.com/maven/"

libraryDependencies <+= scalaVersion { sv =>
  "com.dongxiguo" % ("zero-log_" + sv) % "0.1.0"
}

version := "0.1-SNAPSHOT"

publishTo :=
  Some(Resolver.file("Github Pages", file("../atry.github.com/maven")))

pomExtra <<= scalaVersion { version =>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <scala.version>{version}</scala.version>
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
