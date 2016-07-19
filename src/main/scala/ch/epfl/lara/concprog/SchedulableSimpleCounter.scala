package ch.epfl.lara.concprog

import scala.annotation.tailrec
import instrumentation._
import monitors._

trait Internals { self: SchedulableSimpleCounter =>
  import scheduler._
  
  override def value_=(i: Int) = exec { v = i } (s"Going to write v: $v -> $i", Some(res => s"Written value = $v"))
  override def value: Int = exec { v } (s"Read  value  -> $v")
}

class SchedulableSimpleCounter(init_value: Int, val scheduler: Scheduler) extends SimpleCounter(init_value) with SchedulableMonitor with Internals
