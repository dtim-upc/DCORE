# DistributedCER2

Distributed CER based on CORE engine.

## Installation

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

``` sh
sbt compile
```

Compilation should work out-of-box. Feel free to open an [issue](https://github.com/dtim-upc/DistributedCER2/) if it does not.

### Unmanaged dependencies

This project depends on [CORE](https://github.com/dtim-upc/CORE). 
The current setup uses the JAR from `/lib` which has been created through the `gradle fatJar` from [CORE](https://github.com/dtim-upc/CORE).
In the future, we could properly integrate both projects. For example, SBT can depend on a local project and Maven can publish to the local repository.

## Usage

Change the configuration at `src/main/resources/application.conf`:

- Number of workers per node (~ OS threads)
- Warm up time (seconds)
- Distribution strategy
- Second order predicate complexity
- Query path
- ...

### Demo

The following command will run 1 Engine and 'n' Workers in a single JVM.
All the communications will be inside the JVM i.e. this is not a realistic demo.

``` sh
sbt "run --demo"
```

The following command will run 1 Engine and 'n' Workers, each own on its own JVM.
This is more realistic than the previous command, although, it is not running on  different machines.

```sh
sbt "core/multi-jvm:run Sample"
```

### Production

``` sh
# Machine 1
sbt "run --role engine"

# Machine 2
sbt "run --role worker"

# (Optional) Machine N
sbt "run --role worker"
```

## Running the tests

``` sh
$ sbt test
```

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