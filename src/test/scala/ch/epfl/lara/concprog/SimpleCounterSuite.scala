package ch.epfl.lara.concprog

import scala.concurrent._
import scala.concurrent.duration._
import scala.collection.mutable.HashMap
import scala.util.Random

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SimpleCounterSuite extends FunSuite {
  import instrumentation.TestHelper._
  import instrumentation.TestUtils._
  
  test("Should work without threads by changing 0 to 1") {
    val l = new SimpleCounter(0)
    var r = l.compareAndSet(0, 1)
    assert(r)
    assert(l.get == 1)
  }
  
  test("Should read what one thread inserts") {
    val l = new SimpleCounter(0)
    val r = l.compareAndSet(1, 2)
    assert(r == false)
    assert(l.get == 0)
  }
  
  test("should insert in parallel 1, 2 and 3 in the list [0, 4]") {
    testManySchedules(2, sched => {
      val sc = new SchedulableSimpleCounter(0, sched)
      ( for (i <- (1 to 2).toList) yield
            () => sc.compareAndSet(0, i),
       results => {
        val t1 = results(0) // Was able to change the counter from 0 to 1
        val t2 = results(1) // Was able to change the counter from 0 to 2
        val r = sc.get
        ((t1 == false && t2 == true && r == 2) || (t1 == true && t2 == false && r == 1),
        s"Augment from 0 to 1? $t1. Augment from 0 to 2? $t2. Result? "+r)
      })
    })
  }
}

