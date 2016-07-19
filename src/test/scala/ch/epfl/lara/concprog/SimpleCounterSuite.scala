package ch.epfl.lara.concprog

import scala.concurrent._
import scala.concurrent.duration._
import scala.collection.mutable.HashMap
import scala.util.Random

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import instrumentation.TestHelper._
import instrumentation.TestUtils._
  
import instrumentation._ 
import monitors._

import java.util.concurrent.atomic._
import scala.annotation.tailrec

@RunWith(classOf[JUnitRunner])
class SimpleCounterSuite extends FunSuite {
  
  // Tests for students
  
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
  
  // Test for the lilbrary itself.
  
  def convertTrace(s: String) = "\n".r.replaceAllIn(s, "\n|  ")

  /** A wrong implementation of the Simple counter where synchronized has been omitted */
  class SimpleCounterWrong(init_value: Int) extends AbstractSimpleCounter {
    value = init_value

    def compareAndSet(ifValue: Int, newValue: Int) = { // Forgot the synchronized.
      if (ifValue == value) {
        value = newValue
        true
      } else false
    }

    def get = value
  }
  
  class SchedulableSimpleCounterWrong(init_value: Int, val scheduler: Scheduler) extends SimpleCounterWrong(init_value) with SchedulableMonitor with ValueWrapper
  
  test("should detect wrong implementations of the counter. ") {
    failsOrTimesOut("Did not detect any failure in the implementation but it had some.")(
      testManySchedules(2, sched => {
        val sc = new SchedulableSimpleCounterWrong(0, sched)
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
    )( error =>
      println("Correctly caught the following error:" + convertTrace(error.toString))
    )
  }
  
  /** An implementation of the Simple counter where synchronized is present but the specification says lock-free */
  class SimpleCounterLockFreeWrong(init_value: Int) extends AbstractSimpleCounter {
    value = init_value

    def compareAndSet(ifValue: Int, newValue: Int) = synchronized { // This synchronized should not be there.
      if (ifValue == value) {
        value = newValue
        true
      } else false
    }

    def get = value
  }
  
  class SchedulableSimpleCounterLockFreeWrong(init_value: Int, val scheduler: Scheduler) extends SimpleCounterLockFreeWrong(init_value) with SchedulableMonitor with LockFreeMonitor with ValueWrapper
  
  test("should detect synchronization primitives in supposedly lock-free implementations of the counter. ") {
    failsOrTimesOut("Did not prevent the use of synchronized but it was supposed to be lock-free.")(
      testManySchedules(2, sched => {
        val sc = new SchedulableSimpleCounterLockFreeWrong(0, sched)
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
    )( error =>
      println("Correctly caught the following error:" + convertTrace(error.toString.substring(0, 272)))
    )
  }
  
  /** An correct implementation of the lock-free Simple counter */
  abstract class SimpleCounterLockFree(init_value: Int) extends AbstractSimpleCounter {
    value = init_value

    private val atomic = new AtomicBoolean(false)
    
    def compareAndSet(ifValue: Int, newValue: Int) = {
      var continue = true
      var res = false
      while (continue) {
        if (value != ifValue) {
          res = false
          continue = false
        }
        if (atomic.compareAndSet(false, true) ) {
          continue = false
          res = if (ifValue == value) {
            value = newValue
            true
          } else false
          atomic.compareAndSet(true, false)
        }
      }
      res
    }

    def get = value
  }
  
  class SchedulableSimpleCounterLockFree(init_value: Int, val scheduler: Scheduler) extends SimpleCounterLockFree(init_value) with SchedulableMonitor with LockFreeMonitor with ValueWrapper
  
  test("should verify lock-free implementations of the counter. ") {
    testManySchedules(2, sched => {
      val sc = new SchedulableSimpleCounterLockFree(0, sched)
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
  
  /** A wrong implementation of a counter that waits to be updatable. */
  class ReplaceCounterDeadlock(init_value: Int) extends AbstractSimpleCounter {
    value = init_value

    def compareAndSet(ifValue: Int, newValue: Int) = synchronized {
      while (value != ifValue) {
        wait()
      }
      value = newValue
      notify()  // Forgot to use notifyAll.
      true
    }

    def get = value
  }
  
  class SchedulableReplaceCounterDeadlock(init_value: Int, val scheduler: Scheduler) extends ReplaceCounterDeadlock(init_value) with SchedulableMonitor with ValueWrapper
  
  test("should detect when notify is used instead of notifyAll ") {
    failsOrTimesOut("Did not detect any failure in the implementation but it had some.")(
      testManySchedules(3, sched => {
        val sc = new SchedulableReplaceCounterDeadlock(0, sched)
        ( for (i <- (1 to 3).toList) yield
              () => sc.compareAndSet(i-1, i),
         results => {
          val t1 = results(0) // Was able to change the counter from 0 to 1
          val t2 = results(1) // Was able to change the counter from 1 to 2
          val t3 = results(2) // Was able to change the counter from 2 to 3
          val r = sc.get
          (t1 == true && t2 == true && t3 == true && r == 3,
          s"Augment from 0 to 1? $t1. Augment from 1 to 2? $t2. Augment from 2 to 3? $t2. Result? "+r)
        })
      })
    )( error =>
      println("Correctly caught the following error:" + convertTrace(error.toString.substring(0, 550)))
    )
  }
  
  /** Testing interlocking. */
  abstract class InterlockTest {
    def lock1: Monitor
    def lock2: Monitor
  }
  
  abstract class InterlockTestImpl extends InterlockTest {
    def test(lock_1: Boolean) = if (lock_1) {
      lock1.synchronized {
        lock2.synchronized {
          "Success 1"
        }
      }
    } else {
      lock2.synchronized {
        lock1.synchronized {
          "Success 2"
        }
      }
    }
  }
  
  class SchedulableInterlockTestImpl(val scheduler: Scheduler) extends InterlockTestImpl { self =>
    val lock1 = new Monitor with SchedulableMonitor { val scheduler = self.scheduler }
    val lock2 = new Monitor with SchedulableMonitor { val scheduler = self.scheduler }
  }
  
  test("should detect interlocks ") {
    failsOrTimesOut("Did not detect any interlocks in the implementation but it had some.")(
      testManySchedules(2, sched => {
        val sc = new SchedulableInterlockTestImpl(sched)
        ( List(() => sc.test(true), () => sc.test(false)),
         results => {
          val t1 = results(0) // Was able to output the lock
          val t2 = results(1) // Was able to output the lock
          (t1 == "Success 1" && t2 == "Success 2",
          s"expected only successes, got $t1 $t2")
        })
      })
    )( error =>
      println("Correctly caught the following error:" + convertTrace(error.toString.substring(0, 398)))
    )
  }
}

