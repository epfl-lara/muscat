/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog

import instrumentation.monitors.Monitor
import scala.collection._
import java.util.concurrent.atomic._


abstract class AbstractSimpleCounter extends Monitor {
  protected var v: Int = 0
  
  def value_=(i: Int): Unit = v = i // An indivisible operation that is overridable
  def value: Int  = v               // An indivisible operation that is overridable
  
  def compareAndSet(ifValue: Int, newValue: Int): Boolean

  def get: Int
}

