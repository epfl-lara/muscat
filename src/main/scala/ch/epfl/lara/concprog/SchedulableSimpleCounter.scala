/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog

import scala.annotation.tailrec
import instrumentation._
import monitors._

trait ValueWrapper { self: SchedulableMonitor with AbstractSimpleCounter => 
  lazy val s = scheduler
  import s._
  
  // A illustration of how to use `exec` to mark indivisible operations
  // so that their interleavings can be explored by Muscat
  override def value_=(i: Int) = exec { v = i } (s"Going to write v: $v -> $i", Some(res => s"Written value = $v"))
  override def value: Int = exec { v } (s"Read  value  -> $v")
}

// A class used only for testing `SimpleCounter` using Muscat.
class SchedulableSimpleCounter(init_value: Int, val scheduler: Scheduler) extends SimpleCounter(init_value) with SchedulableMonitor with ValueWrapper
