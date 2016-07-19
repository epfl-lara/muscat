# Scala Debug Concurrent
### A lightweight library for debugging concurrent programs written in Scala.

This library is intented to be used especially for pedagogical purposes.

You can test the default assignment with counters.

    sbt test

Use it as a library !

    sbt package

## How to create new exercises

For pedagogical purposes, you may wish to follow the following pattern:

1. Create a `class B` that multiple threads will deal with.
   You can create any kind of data structure you want.
   You can put this class in a file that the students will work on, and implement some parts.
   Variables to monitor should not be in the class itself but you can say they are available in scope.  
   Example:`SimpleCounter.scala`
   
2. Make this `class B extends A` where `abstract class A` is in another file and `extends ch.epfl.lara.concprog.instrumentation.monitors.Monitor`.
   The `abstract class A` should contain overridable methods to manipulate all the variables to monitor.
   It may contain abstract methods that `class B` should implement.  
   Example: `AbstractSimpleCounter.scala`
   
3. Finally, to have a schedulable version, create a `class C` which
   * `extends class B` (possibly with arguments)
   * accept one extra parameter `scheduler` of type `Scheduler`
   * extends one of the available monitors in the package `ch.epfl.lara.concprog.instrumentation.monitors`
     1. `SchedulableMonitor`: To monitor the behavior of the synchronization primitives `synchronized`, `wait`, `notify` and `notifyAll`.
     2. `LockFreeMonitor`: To make synchronization primitives throw an exception. Only read/writes are recorded.
   * `import scheduler._`
   * overrides or defines the methods from class A using customized `exec` statements so that read/writes are monitored.  
   Example: `ScheduledSimpleCounter.scala`


   
