/* Copyright 2009-2016 EPFL, Lausanne */
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
  
  def failsOrTimesOut[T](msgIfNotFailed: String)(action: => T)(ifFailed: java.lang.AssertionError => Unit): Unit= {
    
    try {
      val asyncAction = future {
        action
      }
      Await.result(asyncAction, 2000.millisecond)
    } catch {
      case e: java.lang.AssertionError => return ifFailed(e)
      case e: java.util.concurrent.ExecutionException => 
        e.getCause match {
          case e: java.lang.AssertionError => return ifFailed(e)
          case e => throw e
        }
    }
    assert(false, msgIfNotFailed)
  }
}