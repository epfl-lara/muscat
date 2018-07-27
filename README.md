# MuScaT: Multithreaded Scala Testing Framework

### A lightweight library for unit testing concurrent programs written in Scala

Muscat provides APIs to test your concurrent code under multiple interleavings of shared operations executed by concurrent threads.
The code for the threads, inputs, and assertions to be tested have to be provided by the user much like a unit testing framework. Muscat can systematically explore multiple interleavings between selected operations, marked by
users, when they are executed concurrently by multiple threads.
It can explore interleavings even in the presence of synchronization primitives: `synchronized`, `wait`, `notify`, and `notifyAll`.
Muscat presents an interleaved execution trace for failed test cases, and
can detect and report deadlocks that happen at runtime.

This library is primarly intented to be used for testing small pieces of concurrent code.
It was able to explore several tens of thousands of interleavings per second on such programs.
In principle, Muscat could be used to test arbitrary Scala programs, provided the configuration parameters are set sufficiently high.

### Illustrative Example 

You can find some small illustrative example and a test suite here: `src/main/scala/ch/epfl/lara/concprog/SimpleCounter.Scala` and  `src/test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.Scala`. The example presents a simple concurrent program that implements the `compareAndSet` operation using the `synchronized` construct. It illustrates how to write unit tests using APIs provided by Muscat.
To run the example and test Muscat code do:

    sbt test

To use Muscat into other projects, do:

    sbt package

and import the jar file generated in the `target` directory.
Below we present an overview of how to use Muscat.
For a more detailed description and technical details see the [whitepaper](https://lara.epfl.ch/~kandhada/MuScaT), which appeared in
[Scala 2016](http://conf.researchr.org/track/scala-2016/scala-2016).

## Creating Testable Classes and Marking Atomic Operations

Muscat can be used to test programs that access or synchronize on objects shared across multiple threads.
To be able to test with Muscat, the shared classes must extend a library trait and should wrap their atomic operations within a library construct `exec`.
Below we illustate it on a simple example. (You may want to follow the below described design pattern as it restricts the overheads of scheduling to the unit tests, isolating them from the normal workflow).
Let `class Base` be the class that is accessible by multiple threads and needs to be tested in a concurrent setting.
For example, `class Base` could be a concurrent queue or list.
   
1. Make `class Base` extend (or implement) the trait `ch.epfl.lara.concprog.instrumentation.monitors.Monitor`.
   Now the `synchronized`, `wait`, `notify`, `notifyAll` methods invoked on instances of `class Base` can be instrumented by the APIs provided by Muscat.
   Also, make sure that every indivisible operation such as read/write of a shared mutable field that may run concurrently is a separate (protected) overridable method of the class.
   Example: [`AbstractSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/AbstractSimpleCounter.scala)
   
2. To create a schedulable version of `class Base`, create a `class Derived` which does the following:
   * `extends class Base`
   * accept one extra parameter `scheduler` of type `Scheduler`
   * extends one of the available monitors in the package `ch.epfl.lara.concprog.instrumentation.monitors`
     1. `SchedulableMonitor`- This monitors the behavior of the synchronization primitives `synchronized`, `wait`, `notify` and `notifyAll`.
     2. `SchedulableMonitor with LockFreeMonitor` - This makes synchronization primitives throw an exception, and is meant to test lock-free data structures.
   * does `import scheduler._` at the start of `class Derived`.
   * overrides or defines the indivisible operations from `class Base` wrapping the indivisible operations with `exec`.
     The construct `exec` has the following signature: `exec(operation)(msgA, Some(res => msgB))` where `operation` is a call-by-name parameter that is indivisible operation, `msgA` is the message that is logged before the operation, 
     and `msgB` is the message to log after the operation, which can refer to the result `res` of the computation. 
   Example: [`SchedulableSimpleCounter.scala`](src/main/scala/ch/epfl/lara/concprog/SchedulableSimpleCounter.scala)

   Now the methods of `class Derived` can be tested by Muscat on multiple interleavings of the shared operations marked with `exec` as described shortly. 
   Note that operations marked with `exec` need not necessarily be a read/write of a field but instead marks the granularity of an indivisible operations. Any context-switches within these operations are not explored by Muscat. 

## Writing Unit Tests

You may have a look at the first three test cases in the file
[`SimpleCounterSuite.scala`](src/test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.scala).
Muscat provides two functions `testSequential`  to test single-threaded (or sequential) behavior, and `testManySchedules` to
test multi-threaded (or concurrent) behavior.

1. `testSequential` available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
    takes one argument: a lamdba from a `Scheduler` to  the the code that needs to be tested. The code can access any data structure or methods defined in the program. However, when it accesses `Schedulable` classes such as `class Derived` its operations will be systematicaly interleaved.
    Note that the `Scheduler` argument of the lambda shall be passed to the constructor of `class Derived`, which requires a scheduler.
    The code should return a `(Boolean, String)` pair where the `Boolean` indicates if the test succeeded, and the `String` is displayed if the test failed.
    The operation has a default timeout, which can be adjusted by the user.

3. `testManySchedules` is available in [`ch.epfl.lara.concprog.instrumentation.TestHelper._`](src/test/scala/ch/epfl/lara/concprog/instrumentation/TestHelper.scala).
  It requires 
  * The number `n` of threads to run in parallel.
  * A lambda from `Scheduler` to a pair consisting of:
    1. A list of `n` lambdas accepting no argument and performing some operations. 
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

1. Make `class Base` abstract and add the following to it: 
(You may want to do this only during testing, and restore `Base` back to its original form)
```scala
def scheduler: Scheduler
lazy val ss = scheduler
import ss._
```
2. Make sure `class Base` is never instantiated, only `class Derived` is. You just made the scheduler passed to `Derived` available to class Base.

3. Everywhere you need to add a log line, just do:
```scala
log("Your log line.")
```
It will be prefixed with the thread number in the buggy trace.

## Can I find more examples on how to use the code?

Yes please have a look at the file [`SimpleCounterSuite.scala`](src/test/scala/ch/epfl/lara/concprog/SimpleCounterSuite.scala) and especially the interlock test.
The file has buggy and non-buggy implementations of lock-free and blocking programs
and some examples for how to test them.
Also, the [whitepaper](https://lara.epfl.ch/~kandhada/MuScaT) illustrates the system a producer-consumer example.

### Contacts

Please feel free to contact us if you are intersted in using the library and need some help in getting started.

1. Ravichandhran Madhavan, Github username: ravimad
2. Mikael Mayer, Github username: MikaelMayer
