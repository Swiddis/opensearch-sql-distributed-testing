package properties.observability

import org.scalacheck.Test.{Result, TestCallback}

object PropLogger extends TestCallback {
  override def onPropEval(
      name: String,
      threadIdx: Int,
      succeeded: Int,
      discarded: Int
  ): Unit = {
    System.out.println(
      "name=" + name + " thread=" + threadIdx + " succeed=" + succeeded + " discard=" + discarded
    )
  }

  override def onTestResult(name: String, result: Result): Unit = {
    System.out.println("name=" + name + " result=" + result)
  }
}
