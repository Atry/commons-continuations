/*
 * Copyright 2012 杨博 (Yang Bo)
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

package com.dongxiguo.commons.continuations
import scala.util.continuations._

final class Label(val rest: Label => Unit) {
  final def goto(): Nothing @suspendable =
    shift { (afterGoto: Nothing => Unit) =>
      rest(this)
    }
}

object Label {
  final def apply(): Label @suspendable =
    shift { (label: Label => Unit) =>
      label(new Label(label))
    }
}
// vim: expandtab softtabstop=2 shiftwidth=2
