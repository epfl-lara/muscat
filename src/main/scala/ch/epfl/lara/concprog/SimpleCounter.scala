package ch.epfl.lara.concprog

class SimpleCounter(init_value: Int) extends AbstractSimpleCounter {
  value = init_value

  /* Variables in scope:
  
     var value: Int
     
     You have to implement the methods compareAndSet and get.
     compareAndSet returns true if the thread successfully replaced the value.
   */
  
  def compareAndSet(ifValue: Int, newValue: Int) = synchronized {
    if (ifValue == value) {
      value = newValue
      true
    } else false
  }

  def get = value
}
