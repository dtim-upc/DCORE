# DistributedCER2

Distributed CER based on CORE engine.

## Installation

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

```sh
sbt compile
```

Compilation should work out-of-box. Feel free to open an [issue](https://github.com/dtim-upc/DistributedCER2/) if it does not.

### Unmanaged dependencies

This project depends on [CORE](https://github.com/dtim-upc/CORE). 
The current setup uses the JAR from `/lib` which has been created through the `gradle fatJar` from [CORE](https://github.com/dtim-upc/CORE).
In the future, we could properly integrate both projects. For example, SBT can depend on a local project and Maven can publish to the local repository.

## Usage

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

Benchmarks are located at `benchmark` directory.  This folder contains the code to generate the benchmarks.
Benchmarks are not commited to `git` since they are procedurally generated and paths may be different in different machines.

In order to generate the benchmarks call:

```sh
sbt "benchmark/runMain generator.App"
```

> If you have already generated the benchmarks once, you need to delete their corresponding folders
> to regenerate them.

Once the benchmarks are created, you can run them:

```sh
# Replace X by linear, quadratic or cubic
# Replace Y by the benchmark number
# Replace Z by the query number
sbt "benchmark/multi-jvm:run X.BenchmarkYQueryZ"
```

> In the future, we will have a script to run all those tests automatically

## Contributing

> If you are going to hack on the project, I would recommend installing [bloop](https://scalacenter.github.io/bloop) to speedup compilation and testing.

This project is developed using `Intellij` but also tested on [metals](https://scalameta.org/metals).
So, we focus on being able to compile, test and run the project on `sbt`.

The project is **formatted** using [scalafmt](https://scalameta.org/scalafmt/docs/installation.html).
You can format all sources by calling: `sbt scalafmt`

Before commiting:
- Check the code compiles: `sbt compile`
- Check the tests pass: `sbt test`
- Check the sources are formatted: `sbt scalafmtCheck`
