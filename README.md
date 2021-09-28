# DistributedCER2

Distributed CER based on CORE engine.

## Build project

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

``` sh
sbt compile
```

Compilation should work out-of-box. Feel free to open an [issue](https://github.com/dtim-upc/DistributedCER2/) if it does not.

> If you are going to hack on the project, I would also recommend installing [bloop](https://scalacenter.github.io/bloop) to speedup compilation and testing.

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

``` sh
# Machine 1
sbt "run --demo"
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
