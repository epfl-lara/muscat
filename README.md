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
   Example:[`SimpleCounter.scala!`](src/main/scala/ch/epfl/lara/concprog/SimpleCounter.scala)
   
2. Make this `class B extends A` where `abstract class A` is in another file and `extends ch.epfl.lara.concprog.instrumentation.monitors.Monitor`.
   The `abstract class A` should contain overridable methods to manipulate all the variables to monitor.
   It may contain abstract methods that `class B` should implement.  
   Example: [`AbstractSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/AbstractSimpleCounter.scala)
   
3. Finally, to have a schedulable version, create a `class C` which
   * `extends class B` (possibly with arguments)
   * accept one extra parameter `scheduler` of type `Scheduler`
   * extends one of the available monitors in the package `ch.epfl.lara.concprog.instrumentation.monitors`
     1. `SchedulableMonitor`: To monitor the behavior of the synchronization primitives `synchronized`, `wait`, `notify` and `notifyAll`.
     2. `LockFreeMonitor`: To make synchronization primitives throw an exception. Only read/writes are recorded.
   * `import scheduler._`
   * overrides or defines the methods from `abstract class A` wrapping the assignments with `exec` so that read/writes are monitored.  The syntax is: `exec(operation)(msgA, Some(res => msgB))` where `operation` is the operation to perform, `msgA` is the message that is logged before the operation, and `msgB` is the message to log after the operation, possibly with the result `res`. You can ignore this second argument.
   
   Example: [`ScheduledSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/ScheduledSimpleCounter.scala)

## How to create tests

You may have a look at the file  [`SimpleCounterSuite.scala`](src/main/scala/ch/epfl/lara/concprog/ScheduledSimpleCounter.scala).
You have essentially three ways of writing test contents. The first two are single-threaded, the third one is multi-threaded.

1. Using `class B`. You can just create an instance of `class B`, run any methods and check assertions.
   In the case of errors, the entire test will fail and no

2. Using `class C` with `testSequential` available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
   It requires a single lambda providing a scheduler to feed `class C` with, which returns a tuple with:
  1. A result after performing some operations (e.g. on one or many element of class C).
  2. A lambda which given the result, returns a pair `(Boolean, String)` where the `Boolean` indicates if the test succeeded, and the `String` is displayed if the test failed.

3. Using `class C` with `testManySchedules` available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
  It requires 
  * The number `n` of threads to create a scheduling.
  * A lambda providing a scheduler to feed `class C` with, which returns a tuple with:
    1. A list of `n` lambdas accepting no argument and performing some operations (e.g. on one or many element of class C).
    2. A lambda which to the result of the `n` threads (encoded as a `List[Any]`), returns a pair `(Boolean, String)` where the `Boolean` indicates if the test succeeded, and the `String` is displayed if the test failed.
