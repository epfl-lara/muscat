/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog.instrumentation

import java.util.concurrent._;
import scala.concurrent.duration._
import scala.collection.mutable._
import Stats._

import java.util.concurrent.atomic.AtomicInteger

sealed abstract class Result
case class RetVal(rets: List[Any]) extends Result
case class Except(msg: String, stackTrace: Array[StackTraceElement]) extends Result
case class Timeout(msg: String) extends Result

/**
 * A class that maintains schedule and a set of thread ids.
 * The schedules are advanced after an operation of a SchedulableBuffer is performed.
 * Note: the real schedule that is executed may deviate from the input schedule
 * due to the adjustments that had to be made for locks
 */
class Scheduler(sched: List[Int]) {
  val maxOps = 500 // a limit on the maximum number of operations the code is allowed to perform

  private var schedule = sched
  private var numThreads = 0
  private val realToFakeThreadId = Map[Long, Int]()
  private val opLog = ListBuffer[String]() // a mutable list (used for efficient concat)   
  private val threadStates = Map[Int, ThreadState]()

  /**
   * Runs a set of operations in parallel as per the schedule.
   * Each operation may consist of many primitive operations like reads or writes
   * to shared data structure each of which should be executed using the function `exec`.
   * @timeout in milliseconds
   * @return true - all threads completed on time,  false -some tests timed out.
   */
  def runInParallel(timeout: Long, ops: List[() => Any]): Result = {
    numThreads = ops.length
    val threadRes = Array.fill(numThreads) { None: Any }
    var exception: Option[Except] = None
    val syncObject = new Object()
    var completed = new AtomicInteger(0)
    // create threads    
    val threads = ops.zipWithIndex.map {
      case (op, i) =>
        new Thread(new Runnable() {
          def run() {
            val fakeId = i + 1
            setThreadId(fakeId)
            try {
              updateThreadState(Start)
              val res = op()
              updateThreadState(End)
              threadRes(i) = res
              // notify the master thread if all threads have completed 
              if (completed.incrementAndGet() == ops.length) {
                syncObject.synchronized { syncObject.notifyAll() }
              }
            } catch {
              case e: Throwable if exception != None => // do nothing here and silently fail
              case e: Throwable =>
                log(s"throw ${e.toString}")                
                exception = Some(Except(s"Thread $fakeId crashed on the following schedule: \n" + opLog.mkString("\n"), 
                    e.getStackTrace))
                syncObject.synchronized { syncObject.notifyAll() }
              //println(s"$fakeId: ${e.toString}")
              //Runtime.getRuntime().halt(0) //exit the JVM and all running threads (no other way to kill other threads)                
            }
          }
        })
    }
    // start all threads
    threads.foreach(_.start())
    // wait for all threads to complete, or for an exception to be thrown, or for the time out to expire
    var remTime = timeout
    syncObject.synchronized {

      timed { if(completed.get() != ops.length) syncObject.wait(timeout) } { time => remTime -= time }
    }
    if (exception.isDefined) {
      exception.get
    } else if (remTime <= 1) { // timeout ? using 1 instead of zero to allow for some errors
      Timeout(opLog.mkString("\n"))
    } else {
      // every thing executed normally
      RetVal(threadRes.toList)
    }
  }

  // Updates the state of the current thread
  def updateThreadState(state: ThreadState): Unit = {
    val tid = threadId
    synchronized {
      threadStates(tid) = state
    }
    state match {
      case Sync(lockToAquire, locks, expectedResultingLocks) =>
        if (locks.indexOf(lockToAquire) < 0) waitForTurn else {
          // Re-aqcuiring the same lock
          updateThreadState(Running(expectedResultingLocks))
        }
      case Start      => waitStart()
      case End        => removeFromSchedule(tid)
      case Running(_) =>
      case _          => waitForTurn // Wait, SyncUnique, VariableReadWrite
    }
  }

  def waitStart() {
    //while (threadStates.size < numThreads) {
    //Thread.sleep(1)
    //}
    synchronized {
      if (threadStates.size < numThreads) {
        wait()
      } else {
        notifyAll()
      }
    }
  }

  def threadLocks = {
    synchronized {
      threadStates(threadId).locks
    }
  }

  def threadState = {
    synchronized {
      threadStates(threadId)
    }
  }

  def mapOtherStates(f: ThreadState => ThreadState) = {
    val exception = threadId
    synchronized {
      for (k <- threadStates.keys if k != exception) {
        threadStates(k) = f(threadStates(k))
      }
    }
  }

  def log(str: String) = {
    if((realToFakeThreadId contains Thread.currentThread().getId())) {
      val space = (" " * ((threadId - 1) * 2)) 
      val s = space + threadId + ":" + "\n".r.replaceAllIn(str, "\n" + space + "  ")
      opLog += s
    }
  }

  /**
   * Executes a read or write operation to a global data structure as per the given schedule
   * @param msg a message corresponding to the operation that will be logged
   */
  def exec[T](primop: => T)(msg: => String, postMsg: => Option[T => String] = None): T = {
    if(! (realToFakeThreadId contains Thread.currentThread().getId())) {
      primop
    } else {
      updateThreadState(VariableReadWrite(threadLocks))
      val m = msg
      if(m != "") log(m)
      if (opLog.size > maxOps)
        throw new Exception(s"Total number of reads/writes performed by threads exceed $maxOps. A possible deadlock!")
      val res = primop
      postMsg match {
        case Some(m) => log(m(res))
        case None =>
      }
      res
    }
  }

  private def setThreadId(fakeId: Int) = synchronized {
    realToFakeThreadId(Thread.currentThread.getId) = fakeId
  }

  def threadId = 
    try {
      realToFakeThreadId(Thread.currentThread().getId())
    } catch {
    case e: NoSuchElementException =>
      throw new Exception("You are accessing shared variables in the constructor. This is not allowed. The variables are already initialized!")
    }

  private def isTurn(tid: Int) = synchronized {
    (!schedule.isEmpty && schedule.head != tid)
  }

  def canProceed(): Boolean = {
    val tid = threadId
    canContinue match {
      case Some((i, state)) if i == tid =>
        //println(s"$tid: Runs ! Was in state $state")
        canContinue = None
        state match {
          case Sync(lockToAquire, locks, expectedResultingLocks) => updateThreadState(Running(expectedResultingLocks))
          case SyncUnique(lockToAquire, locks, expectedResultingLocks) =>
            mapOtherStates {
              _ match {
                case SyncUnique(lockToAquire2, locks2, expectedResultingLocks) if lockToAquire2 == lockToAquire => Wait(lockToAquire2, locks2, expectedResultingLocks)
                case e => e
              }
            }
            updateThreadState(Running(expectedResultingLocks))
          case VariableReadWrite(locks) => updateThreadState(Running(locks))
        }
        true
      case Some((i, state)) =>
        //println(s"$tid: not my turn but $i !")
        false
      case None =>
        false
    }
  }

  var threadPreference = 0 // In the case the schedule is over, which thread should have the preference to execute.

  /** returns true if the thread can continue to execute, and false otherwise */
  def decide(): Option[(Int, ThreadState)] = {
    if (!threadStates.isEmpty) { // The last thread who enters the decision loop takes the decision.
      //println(s"$threadId: I'm taking a decision")
      if (threadStates.values.forall { case e: Wait => true case _ => false }) {
        val waiting = threadStates.keys.map(_.toString).mkString(", ")
        val (s, are, them) = if (threadStates.size > 1) ("s", "are", "them") else ("", "is", "it")
        throw new Exception(s"Deadlock: Thread$s $waiting $are waiting but all others have ended and cannot notify $them.")
      } else {
        // Threads can be in Wait, Sync, SyncUnique, and VariableReadWrite mode.
        // Let's determine which ones can continue.
        val notFree = threadStates.collect { case (id, state) => state.locks }.flatten.toSet
        val threadsNotBlocked = threadStates.toSeq.filter {
          case (id, v: VariableReadWrite)         => true
          case (id, v: CanContinueIfAcquiresLock) => !notFree(v.lockToAquire) || (v.locks contains v.lockToAquire)
          case _                                  => false
        }
        if (threadsNotBlocked.isEmpty) {
          val waiting = threadStates.keys.map(_.toString).mkString(", ")
          val s = if (threadStates.size > 1) "s" else ""
          val are = if (threadStates.size > 1) "are" else "is"
          val whoHasLock = threadStates.toSeq.flatMap { case (id, state) => state.locks.map(lock => (lock, id)) }.toMap
          val reason = threadStates.collect {
            case (id, state: CanContinueIfAcquiresLock) if notFree(state.lockToAquire) =>
              s"Thread $id is waiting on a lock held by thread ${whoHasLock(state.lockToAquire)}"
          }.mkString("\n")
          throw new Exception(s"Deadlock: Thread$s $waiting are interlocked. "+( if (reason != "") s"Indeed:\n$reason" else ""))
        } else if (threadsNotBlocked.size == 1) { // Do not consume the schedule if only one thread can execute.
          Some(threadsNotBlocked(0))
        } else {
          val next = schedule.indexWhere(t => threadsNotBlocked.exists { case (id, state) => id == t })
          if (next != -1) {
            //println(s"$threadId: schedule is $schedule, next chosen is ${schedule(next)}")
            val chosenOne = schedule(next) // TODO: Make schedule a mutable list.
            schedule = schedule.take(next) ++ schedule.drop(next + 1)
            Some((chosenOne, threadStates(chosenOne)))
          } else {
            threadPreference = (threadPreference + 1) % threadsNotBlocked.size
            val chosenOne = threadsNotBlocked(threadPreference) // Maybe another strategy
            Some(chosenOne)
            //threadsNotBlocked.indexOf(threadId) >= 0
            /*
            val tnb = threadsNotBlocked.map(_._1).mkString(",")
            val s = if (schedule.isEmpty) "empty" else schedule.mkString(",")
            val only = if (schedule.isEmpty) "" else " only"
            throw new Exception(s"The schedule is $s but$only threads ${tnb} can continue")*/
          }
        }
      }
    } else canContinue
  }

  /**
   * This will be called before a schedulable operation begins.
   * This should not use synchronized
   */
  var numThreadsWaiting = new AtomicInteger(0)
  //var waitingForDecision = Map[Int, Option[Int]]() // Mapping from thread ids to a number indicating who is going to make the choice.
  var canContinue: Option[(Int, ThreadState)] = None // The result of the decision thread Id of the thread authorized to continue.
  private def waitForTurn = {
    synchronized {
      if (numThreadsWaiting.incrementAndGet() == threadStates.size) {
        canContinue = decide()
        notifyAll()
      }
      //waitingForDecision(threadId) = Some(numThreadsWaiting)
      //println(s"$threadId Entering waiting with ticket number $numThreadsWaiting/${waitingForDecision.size}")
      while (!canProceed()) wait()
    } 
    numThreadsWaiting.decrementAndGet()
  }

  /**
   * To be invoked when a thread is about to complete
   */
  private def removeFromSchedule(fakeid: Int) = synchronized {
    //println(s"$fakeid: I'm taking a decision because I finished")
    schedule = schedule.filterNot(_ == fakeid)
    threadStates -= fakeid
    if (numThreadsWaiting.get() == threadStates.size) {
      canContinue = decide()
      notifyAll()
    }
  }

  def getOperationLog() = opLog
}
