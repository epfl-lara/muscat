/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog
package instrumentation
package monitors

class Dummy

trait Monitor {
  implicit val dummy: Dummy = new Dummy
  
  def wait()(implicit i: Dummy) = waitDefault()
  
  def synchronized[T](e: => T) = synchronizedDefault(e)
  
  def notify()(implicit i: Dummy) = notifyDefault()
  
  def notifyAll()(implicit i: Dummy) = notifyAllDefault()
  
  private val lock = new AnyRef

  // Can be overriden.
  def waitDefault(): Unit = lock.wait()
  def synchronizedDefault[T](toExecute: =>T): T = lock.synchronized(toExecute)
  def notifyDefault(): Unit = lock.notify()
  def notifyAllDefault(): Unit = lock.notifyAll()
}