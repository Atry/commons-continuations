import sbt._
import Keys._

object PluginBuild extends Build {
  // xsbt-gpg-plugin 0.4 is the last version compatible with sbt 0.12
  lazy val xsbtGpgPlugin =
    RootProject(uri("git://github.com/sbt/xsbt-gpg-plugin.git#0.4"))

  lazy val root =
    Project(id = "root", base = file(".")).dependsOn(xsbtGpgPlugin)

}

// vim: set ts=2 sw=2 et:
