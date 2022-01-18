# DCORE

The goal of this project is to empirically demonstrate the performance gains of the distribution of Complex Event Recognition.
Particularly, the enumeration process and the evaluation of second order predicates.

You can find a copy of the thesis [here](./Distributed_Complex_Event_Recognition-Arnau_Abela.pdf).

This project depends on [CORE](https://github.com/dtim-upc/CORE) (private) and [CORE2](https://github.com/dtim-upc/CORE2).

## Installation

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `jdk11/openjdk11` and `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

Then, go to the root of this project and execute:

```sh
sbt compile
```

Compilation should work out-of-box. Feel free to open an [issue](https://github.com/dtim-upc/DistributedCER2/) if it does not.

## Usage

The follow section will guide you on how to use the main _CLI_ executable of `dcer`.

All available options:

```sh
$ sbt "run --help"

Usage:
    dcer core
    dcer core2
A distributed complex event processing engine.
Options and flags:
    --help
        Display this help text.
Subcommands:
    core
        Execute using CORE.
    core2
        Execute using CORE2.
```

```sh
$ sbt "run core --help"

Usage:
    dcer core --demo
    dcer core --role <string> [--port <string>] [--query <path>] [--strategy <string>]
Execute using CORE.
Options and flags:
    --help
        Display this help text.
    --demo
        Run the demo
    --role <string>
        Available roles: List(Master, Slave)
    --port <string>
        Available ports: [1024, 49152]
    --query <path>
        Examples at './core/src/main/resources/'
    --strategy <string>
        Available strategies: List(Sequential, RoundRobin, RoundRobinWeighted, PowerOfTwoChoices, MaximalMatchesEnumeration, MaximalMatchesDisjointEnumeration)
```

```sh
$ sbt "run core2 --help"

Usage: dcer core2 --role <string> [--port <string>] [--query <path>] [--strategy <string>]
Execute using CORE2.
Options and flags:
    --help
        Display this help text.
    --role <string>
        Available roles: List(Master, Slave)
    --port <string>
        Available ports: [1024, 49152]
    --query <path>
        Examples at './core/src/main/resources/'
    --strategy <string>
        Available strategies: List(Sequential, Distributed)
```

### Demo

The following command will run 1 Engine and 'n' Workers in a single JVM.
All the communications will be inside the JVM i.e. this is not a realistic demo.

```sh
sbt "run core --demo"
```

### Execution on a cluster

On a single machine of the cluster, you need to run the program as **master**:

```sh
sbt "run core --role master"
```

On the rest of the machines, you need to run the program as **slave**:

```
sbt "run core --role slave"
```

You usually run the slaves first, and later the master. 
The master waits **10 seconds** for all the slaves to connect to the network.
And then, starts processing the configured query.

#### Configuration

Change the configuration at `core/src/main/resources/application.conf`:

- Number of workers per node (~ OS threads)
- Warm up time (seconds)
- Distribution strategy
- Second order predicate complexity
- Query path
- ...

## Running the tests

> Integration tests should be run separately from unit tests.

```sh
$ sbt "testOnly dcer.unit*"

$ sbt "testOnly dcer.integration*"
```

## Running the benchmarks

> Benchmarks are located at `benchmark` directory.  This folder contains the code to generate the benchmarks.
> Benchmarks are not committed to `git` since they are procedurally generated and paths may be different in different machines.

In order to run the benchmarks you need to install [stack](https://docs.haskellstack.org/en/stable/README/) (follow the instructions from the link).
Stack is a package manager for Haskell. The script `run-benchmarks.hs` is written in haskell and uses some libraries. Stack will automatically install all dependencies for you.

To see all available options:

```sh
$ ./run-benchmarks.hs --help
Benchmarks Script

Usage: run-benchmarks.hs (all | only) [-c|--clean] [-o|--output OUTPUT]

Available options:
  -h,--help                Show this help text
  -c,--clean               Clean run
  -o,--output OUTPUT       Output folder

Available commands:
  all                      Run all benchmarks
  only                     Run only the given benchmark  
```

### Running all the benchmarks

To generate the benchmarks, run them and collect all the statistics (execution time, coefficient of variation, etc):

```sh
./run-benchmarks.hs all --clean --output output
```

Benchmarks can also be run atomically by hand: 

```sh
# Replace X by the benchmark: benchmark0, benchmark1.
# Replace Y by the query: query1, query2 ...
# Replace V by the number of workers: workers4, workers8, workers12.
# Replace W by complexity: linear, quadratic or cubic.
# Replace Z by strategy: Sequential, RoundRobin, RoundRobinWeighted, PowerOfTwoChoices, MaximalMatchesEnumeration.
sbt "benchmark/multi-jvm:run X.Y.V.W.Z"

# For example
sbt "benchmark/multi-jvm:run benchmark0.query1.workers12.linear.RoundRobin" 
```

The output of the manual execution should be stored at `benchmark/target/*`

The manual execution outputs an exception that it is **expected**.

```shell
Exception in thread "Thread-1" java.lang.InterruptedException
        at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.reportInterruptAfterWait(AbstractQueuedSynchronizer.java:2056)
        at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2133)
        at java.base/java.util.concurrent.LinkedBlockingQueue.poll(LinkedBlockingQueue.java:458)
        at sbt.internal.util.Terminal$proxyInputStream$.poll$1(Terminal.scala:675)
        at sbt.internal.util.Terminal$proxyInputStream$.read(Terminal.scala:681)
        at sbt.internal.util.Terminal$SimpleInputStream.read(Terminal.scala:619)
        at sbt.internal.util.Terminal$SimpleInputStream.read$(Terminal.scala:618)
        at sbt.internal.util.Terminal$proxyInputStream$.read(Terminal.scala:627)
        at java.base/java.io.FilterInputStream.read(FilterInputStream.java:133)
        at java.base/java.io.FilterInputStream.read(FilterInputStream.java:107)
        at scala.sys.process.BasicIO$.loop$1(BasicIO.scala:238)
        at scala.sys.process.BasicIO$.transferFullyImpl(BasicIO.scala:246)
        at scala.sys.process.BasicIO$.transferFully(BasicIO.scala:227)
        at scala.sys.process.BasicIO$.connectToIn(BasicIO.scala:196)
        at scala.sys.process.BasicIO$.$anonfun$input$1(BasicIO.scala:203)
        at scala.sys.process.BasicIO$.$anonfun$input$1$adapted(BasicIO.scala:202)
        at scala.sys.process.ProcessBuilderImpl$Simple.$anonfun$run$2(ProcessBuilderImpl.scala:79)
        at scala.sys.process.ProcessImpl$Spawn$$anon$1.run(ProcessImpl.scala:27)
```

#### Getting hardware info on Linux

```sh
uname -a # Kernel version
lshw -short # nix-shell -p lshw --run 'lshw -short' # CPU, RAM, etc
java -version # OpenJDK version
```

## Contributing

ns> If you are going to hack on the project, I would recommend installing [bloop](https://scalacenter.github.io/bloop) to speedup compilation and testing.

This project is developed using `Intellij` but also tested on [metals](https://scalameta.org/metals).
So, we focus on being able to compile, test and run the project on `sbt`.

The project is **formatted** using [scalafmt](https://scalameta.org/scalafmt/docs/installation.html).
You can format all sources by calling: `sbt scalafmt`

Before committing:
- Check the code compiles: `sbt compile`
- Check the tests pass: `sbt test`
- Check the sources are formatted: `sbt scalafmtCheck`
