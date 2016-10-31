/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog.instrumentation

abstract class ThreadState {
  def locks: Seq[AnyRef]
}
trait CanContinueIfAcquiresLock extends ThreadState {
  def lockToAquire: AnyRef
}
case object Start extends ThreadState { def locks: Seq[AnyRef] = Seq.empty }
case object End extends ThreadState { def locks: Seq[AnyRef] = Seq.empty }
case class Wait(lockToAquire: AnyRef, locks: Seq[AnyRef], expectedResultingLocks: Seq[AnyRef]) extends ThreadState
case class Sync(lockToAquire: AnyRef, locks: Seq[AnyRef], expectedResultingLocks: Seq[AnyRef]) extends ThreadState with CanContinueIfAcquiresLock
case class Running(locks: Seq[AnyRef]) extends ThreadState
case class VariableReadWrite(locks: Seq[AnyRef]) extends ThreadState