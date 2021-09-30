import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val LibraryVersion = "0.1.0"
val ScalaVersion = "2.12.14"

// Lib
val AkkaVersion = "2.6.16"
val LogbackVersion = "1.2.6"
val DeclineVersion = "2.2.0"

// Testing
val ScalaTestVersion = "3.1.4"

// Plugins
val BetterMonadForVersion = "0.3.1"

val LibraryDependencies =
  Seq(
    // Distributed and concurrent programming
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    // Logging
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    // CLI parsing
    "com.monovore" %% "decline" % DeclineVersion
  )

val TestDependencies =
  Seq(
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion,
    "org.scalatest" %% "scalatest-funspec" % "3.2.9"
  ).map(_ % Test)

val initHeapSizeOpt = "-Xms128m"
val maxHeapSizeOpt = "-Xmx1024m"

lazy val root = (project in file("."))
  .enablePlugins(
    // https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html
    JavaAppPackaging
  )
  .enablePlugins(MultiJvmPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    name := "DistributedCER",
    version := LibraryVersion,
    scalaVersion := ScalaVersion,
    run / javaOptions ++= Seq(initHeapSizeOpt, maxHeapSizeOpt),
    MultiJvm / javaOptions ++= Seq(initHeapSizeOpt, maxHeapSizeOpt),
    run / fork := false,
    Global / cancelable := false,
    Test / parallelExecution := false,
    unmanagedBase := baseDirectory.value / "lib", // `sbt show unmanagedJars` to get a list of unmanaged jars
    libraryDependencies ++= (LibraryDependencies ++ TestDependencies),
    addCompilerPlugin(
      "com.olegpy" %% "better-monadic-for" % BetterMonadForVersion
    )
  )
  // https://doc.akka.io/docs/akka/current/multi-jvm-testing.html?language=scala#multi-jvm-testing
  .configs(MultiJvm)
