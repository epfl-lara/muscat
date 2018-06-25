# MuScaT: Multithreaded Scala Testing Framework

### A lightweight library for unit testing concurrent programs written in Scala

The library provides APIs to explore multiple interleavings of shared operations executed by concurrent threads. 
The code for the threads, inputs, and assertions to be tested have to be provided by the user much like a unit testing 
framework. The library can systematically explore multiple interleavings between selected operations, marked by
users, executed concurrently.
It can test interleavings even in the presence of synchronization primitives: `synchronized`, `wait`, `notify`, and `notifyAll`.
Furthermore, the library can provide an interleaved execution trace for failed test cases, and 
can detect and report deadlocks that happen at runtime.

This library is primarly intented to be used for testing small pieces of concurrent code e.g. student assignments.
It was able to explore several tens of thousands of interleavings per second on such programs.
In principle, the library could be used to test arbitrary Scala programs, provided the configuration parameters are 
set sufficiently high.

### Illustrative Example 

You can find a small illustrative example in the directory: `src/test/.../SimpleCounterSuite.Scala`.
The example illustrates how to write unit test using the library API. 
To run the example and test the library code do:

    sbt test

To use the library into other projects, do:

    sbt package

and import the jar file generated in the `target` directory.
Below we present an overview of how to use the library.
For a more detailed description and technical details see the [whitepaper](https://lara.epfl.ch/~kandhada/MuScaT), which is to appear in 
[Scala 2016](http://conf.researchr.org/track/scala-2016/scala-2016)

## Creating Testable Classes and Marking Atomic Operations

The library can be used to test programs that modify/access or `synchronize` on objects shared across multiple threads.
To do so the shared classes must extend a library trait and should wrap their atomic operations within a 
library construct `exec`.
Below we illustate it on a simple example. (You may want to follow the below described design pattern as it restricts 
the overheads of scheduling to the unit tests, isolating them from the normal workflow).
Let `class B` be the class that is accessible by multiple threads and needs to tested in a concurrent setting.
For example, `class B` could be a concurrent queue or list.
   
1. Make `class B` extend (or implement) the trait `ch.epfl.lara.concprog.instrumentation.monitors.Monitor`.
   Now the `synchronized`, `wait`, `notify`, `notifyAll` methods invoked on instances of `class B` can be instrumented
   by the library APIs.
   Also, make sure that every atomic operation such as read/write of a shared mutable field used by the methods of the class 
   that may run concurrently is a separate (protected) overridable method of the class.
   Example: [`AbstractSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/AbstractSimpleCounter.scala)
   
2. To create a schedulable version of `class B`, create a `class C` which
   * `extends class B` 
   * accept one extra parameter `scheduler` of type `Scheduler`
   * extends one of the available monitors in the package `ch.epfl.lara.concprog.instrumentation.monitors`
     1. `SchedulableMonitor`: To monitor the behavior of the synchronization primitives `synchronized`, `wait`, `notify` and `notifyAll`.
     2. `SchedulableMonitor with LockFreeMonitor`: To make synchronization primitives throw an exception. Only read/writes are scheduled.
   * does `import scheduler._` at the start of `class C`.
   * overrides or defines the atomic methods from `class B` wrapping the atomic operations with `exec`.
     The syntax is: `exec(operation)(msgA, Some(res => msgB))` where `operation` is the operation to perform, `msgA` is the message that is logged before the operation, 
     and `msgB` is the message to log after the operation, which can refer to the result `res` of the computation. 
   Example: [`SchedulableSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/SchedulableSimpleCounter.scala)

   Now the methods of `class C` can be tested by the library on multiple interleavings of the shared operations marked with `exec` as described shortly. 
   Note that operations marked with `exec` need not necessarily be a read/write of a field but should 
   logically constitute an atomic operation. Otherwise, there may be concurrency bugs that are not tested by the library APIs.

## Writing Unit Tests

You may have a look at the file  [`SimpleCounterSuite.scala`](src/test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.scala).
The library provides two functions `testSequential`  to test single-threaded (or sequential) behavior, and `testManySchedules` to
test multi-threaded (or concurrent) behavior.

1. `testSequential` available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
    takes one argument: a lamdba from a `Scheduler` to  the the code that needs to be tested. The code can access any data structure or methods defined in the program. However, when it accesses `Schedulable` classes such as `class C` its operations will be systematicaly interleaved.
    Note that the `Scheduler` argument of the lambda can be passed to the constructor of `class C`, which requires a scheduler.
    The code should return a `(Boolean, String)` pair where the `Boolean` indicates if the test succeeded, and the `String` is displayed if the test failed.
    The operation has a default timeout, which can be adjusted by the user.

3. Using `class C` with `testManySchedules` available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
  It requires 
  * The number `n` of threads to run in parallel.
  * A lambda from `Scheduler` to:
    1. A list of `n` lambdas accepting no argument and performing some operations (e.g. one or more instances of class C). 
	Each lamdba corresponds to the code that would be executed by the threads
    2. A lambda to which  the result of the `n` threads (encoded as a `List[Any]`) would be fed. The lambda should return a pair `(Boolean, String)` 
	where the `Boolean` indicates if the test succeeded, and the `String` is displayed if the test failed.
	Note that the code executed by threads can also manipulate other mutable state if necessary that is not wrapped inside `exec`. 
	For instance, to track values other than those that are returned. The tool cannot detect bugs resulting because of such mutable states.

## Test Parameters

The exploration of interleavings can be configured using the following parameters found in the file [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala)

1. `contextSwitchBound`: Maximum number of context-switches possible in the schedules generated for testing
2. `readWritesPerThread`: Maximum number of `exec` operations that can be performed by the threads. If more operations are performed by the threads, they would not be tested for multiple interleavings.
3. `noOfSchedules`: Maximum number of schedules or interleavings to test. The interleavings are uniformly randomly sampled by default.
4. `testTimeout`: The time out for a unit test in seconds. A test is considered to have failed if it exceeds the time out.
5. `scheduleTimeout`: The time out for a _single_ interleaving. The interleaving is considered buggy and reported to the user if it exceeds this timeout.

## How to add a custom line in the concurrent trace?

If you need to debug code with complex logic and want to add more statements, you can do the following:

1. Make `class B` abstract and add the following to it: 
(You may want to do this only during testing, and restore `B` back to its original form)
```scala
def scheduler: Scheduler
lazy val ss = scheduler
import ss._
```
2. Make sure `class B` is never instantiated, only `class C` is. You just made the scheduler passed to `C` available to class B.
3. Everywhere you need to add a log line, just do:
```scala
log("Your log line.")
```
It will be prefixed with the thread number in the buggy trace.

## Can I find more examples on how to use the code ?

Yes please have a look at the file [`SimpleCounterSuite.scala`](src/test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.scala) and especially the interlock test.

### Contacts

1. Ravichandhran Madhavan, Github username: ravimad
2. Mikael Mayer, Github username: MikaelMayer
