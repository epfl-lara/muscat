/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog.instrumentation

import java.lang.management._
import scala.collection.JavaConversions._

/**
 * A collection of methods that can be used to collect run-time statistics about Leon programs.
 * This is mostly used to test the resources properties of Leon programs
 */
object Stats {
  def timed[T](code: => T)(cont: Long => Unit): T = {
    var t1 = System.currentTimeMillis()
    val r = code
    cont((System.currentTimeMillis() - t1))
    r
  }

  def withTime[T](code: => T): (T, Long) = {
    var t1 = System.currentTimeMillis()
    val r = code
    (r, (System.currentTimeMillis() - t1))
  }
}
