/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog

/*
 * A simple, pedagogical example that implements a `CompareAndSet`
 * operation using the `synchornized` construct.
 * See `test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.scala`
 * for examples of how to test this code under multiple interleavings
 * using Muscat APIs.
 */
class SimpleCounter(init_value: Int) extends AbstractSimpleCounter {
  value = init_value

  def compareAndSet(ifValue: Int, newValue: Int) = synchronized {
    if (ifValue == value) {
      value = newValue
      true
    } else false
  }

  def get = value
}