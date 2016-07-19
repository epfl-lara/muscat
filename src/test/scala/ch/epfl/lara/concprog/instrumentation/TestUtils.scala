package ch.epfl.lara.concprog
package instrumentation

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object TestUtils {
  def failsOrTimesOut[T](action: => T): Boolean = {
    val asyncAction = future {
      action
    }
    try {
      Await.result(asyncAction, 2000.millisecond)
    } catch {
      case _: Throwable => return true
    }
    return false
  }
}