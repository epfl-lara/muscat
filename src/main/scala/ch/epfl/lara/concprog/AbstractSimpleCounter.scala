package ch.epfl.lara.concprog

import instrumentation.monitors.Monitor
import scala.collection._
import java.util.concurrent.atomic._


abstract class AbstractSimpleCounter extends Monitor {
  protected var v: Int = 0
  
  def value_=(i: Int): Unit = v = i
  def value: Int  = v
}

