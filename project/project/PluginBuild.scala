/*
 * Copyright 2011 杨博 (Yang Bo)
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
