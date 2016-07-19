/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog
package instrumentation
package monitors

trait LockFreeMonitor extends Monitor {
  override def waitDefault() = {
    throw new Exception("Please use lock-free structures and do not use wait()")
  }
  override def synchronizedDefault[T](toExecute: =>T): T = {
    throw new Exception("Please use lock-free structures and do not use synchronized()")
  }
  override def notifyDefault() = {
    throw new Exception("Please use lock-free structures and do not use notify()")
  }
  override def notifyAllDefault() = {
    throw new Exception("Please use lock-free structures and do not use notifyAll()")
  }
}