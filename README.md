# DistributedCER2

Distributed CER based on CORE engine.

## Installation

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `jdk11/openjdk11` and `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

Then, go to the root of this project and execute:

```sh
sbt compile
```

Compilation should work out-of-box. Feel free to open an [issue](https://github.com/dtim-upc/DistributedCER2/) if it does not.

### Unmanaged dependencies

This project depends on [CORE](https://github.com/dtim-upc/CORE). 
The current setup uses the JAR from `/lib` which has been created through the `gradle fatJar` from [CORE](https://github.com/dtim-upc/CORE).
In the future, we could properly integrate both projects. For example, SBT can depend on a local project and Maven can publish to the local repository.

## Usage

The follow section will guide you on how to use the main _CLI_ executable of `dcer`.

All available options:

```sh
$ sbt "run --help"

Usage:
    dcer --demo
    dcer --role <string> [--port <string>] [--query <path>] [--strategy <string>]
    
A distributed complex event processing engine.

Options and flags:
    --help
        Display this help text.
    --demo
        Run the demo
    --role <string>
        Available roles: List(Engine, Worker)
    --port <string>
        Available ports: [1024, 49152]
    --query <path>
        Examples at './core/src/main/resources/'
    --strategy <string>
        Available strategies: List(Sequential, RoundRobin)
```

### Demo

The following command will run 1 Engine and 'n' Workers in a single JVM.
All the communications will be inside the JVM i.e. this is not a realistic demo.

```sh
sbt "run --demo"
```

or

```sh
$ bloop run core -- --demo
```

### Production

```sh
# Machine 1
sbt "run --role engine"

# Machine 2
sbt "run --role worker"

# (Optional) Machine N
sbt "run --role worker"
```

#### Configuration

Change the configuration at `core/src/main/resources/application.conf`:

- Number of workers per node (~ OS threads)
- Warm up time (seconds)
- Distribution strategy
- Second order predicate complexity
- Query path
- ...

## Running the tests

```sh
$ sbt test
```

or

```sh
$ bloop test core-test
# To run a specific test
$ bloop test core-test -o '*OutputValidation*' 
```

> You may see some tests failing. This is expected and must be fixed in the future.
> The problem is that different tests create different Engine but they use the same query files.
> CORE uses a static field to hold a reference to all streams, which clashes between tests execution.

Should run all subproject tests.

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

## Contributing

> If you are going to hack on the project, I would recommend installing [bloop](https://scalacenter.github.io/bloop) to speedup compilation and testing.

This project is developed using `Intellij` but also tested on [metals](https://scalameta.org/metals).
So, we focus on being able to compile, test and run the project on `sbt`.

The project is **formatted** using [scalafmt](https://scalameta.org/scalafmt/docs/installation.html).
You can format all sources by calling: `sbt scalafmt`

Before committing:
- Check the code compiles: `sbt compile`
- Check the tests pass: `sbt test`
- Check the sources are formatted: `sbt scalafmtCheck`