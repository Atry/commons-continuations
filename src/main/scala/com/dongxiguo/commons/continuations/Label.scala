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
