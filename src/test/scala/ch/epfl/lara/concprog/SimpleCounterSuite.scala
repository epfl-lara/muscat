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
  
  test("Should update the value from 0 to 1") {
    val l = new SimpleCounter(0)
    var r = l.compareAndSet(0, 1)
    assert(r)
    assert(l.get == 1)
  }
  
  test("Should not update the value when the test fails") {
    testSequential[(Boolean, Int)]{ sched => 
      val l = new SchedulableSimpleCounter(0, sched)
      val r = l.compareAndSet(1, 2)
      val v = l.get
      if (r != false) (false, s"Expected compareAndset to return false, got $r")
      else if(v != 0) (false, s"Expected compareAndSet not to modify the value, but it set it to $v")
      else (true, "")
    }
  }
  
  test("should make sure that only one thread considers writing successful if they race.") {
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

