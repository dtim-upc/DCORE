# DistributedCER2

Distributed CER.

## Build project

This project is build using `jdk11` or `openjdk11`, [sbt](https://www.scala-sbt.org/index.html) and [scala](https://scala-lang.org/). 

In order to compile, build and run this project you need to install `sbt` in your system (`sbt` will automatically download the right `scala` version of your project).

If you are going to hack on the project, I would also recommend installing [bloop](https://scalacenter.github.io/bloop) which speedup compilation and testing.

### Unmanaged dependencies

This project depends on [CORE](https://github.com/dtim-upc/CORE). 
The current setup uses the JAR from `/lib` which has been created through the `gradle fatJar` from [CORE](https://github.com/dtim-upc/CORE).
In the future, we could properly integrate both projects. For example, SBT can depend on a local project and Maven can publish to the local repository.

## Run tests

sbt:

``` sh
$ sbt
sbt> test
```

bloop:

``` sh
$ bloop test root
```

## Actor Hierarchy

### Proposal 1

This is a `1-n` architecture. 
There is only 1 master, the `Engine` actor, and, n `Worker` actors.
The `Engine` processes the events and sends the maximal matches to the actors using the configured distribution strategy.
Once the `Worker` finishes applying the second-order predicate to the event trends, it sends back to the master the filtered matches.

```
Root - - > EngineManager (1) -- Engine(1)
  |             |
  |             |
   - - - > Worker (1..*)
```

### Proposal 2

To be proposed.
