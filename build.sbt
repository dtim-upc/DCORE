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
val FunSpecVersion = "3.2.9"

// Plugins
val BetterMonadForVersion = "0.3.1"

val initHeapSizeOpt = "-Xms128m"
val maxHeapSizeOpt = "-Xmx1024m"

lazy val commonSettings = Seq(
  version := LibraryVersion,
  scalaVersion := ScalaVersion,
  addCompilerPlugin(
    "com.olegpy" %% "better-monadic-for" % BetterMonadForVersion
  )
)

lazy val root = (project in file("."))
  .aggregate(core, benchmark)
  .enablePlugins(
    // https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html
    JavaAppPackaging
  )
  .settings(
    name := "dcer",
    Compile / run := (core / Compile / run).evaluated,
    // https://github.com/sbt/sbt-native-packager
    Compile / packageBin := (core / Compile / packageBin).value
  )

lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    run / javaOptions ++= Seq(initHeapSizeOpt, maxHeapSizeOpt),
    run / fork := false,
    Global / cancelable := false,
    Test / parallelExecution := false,
    // `sbt show unmanagedJars`
    // CORE is imported through its jar
    unmanagedBase := baseDirectory.value / "lib",
    libraryDependencies ++=
      Seq(
        // Distributed and concurrent programming
        "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
        // Logging
        "ch.qos.logback" % "logback-classic" % LogbackVersion,
        // CLI parsing
        "com.monovore" %% "decline" % DeclineVersion
      ) ++ Seq(
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
        "org.scalatest" %% "scalatest" % ScalaTestVersion,
        "org.scalatest" %% "scalatest-funspec" % FunSpecVersion
      ).map(_ % Test)
  )

lazy val benchmark = (project in file("benchmark"))
  .dependsOn(core)
  .enablePlugins(MultiJvmPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    commonSettings,
    run / fork := false,
    MultiJvm / javaOptions ++= Seq(initHeapSizeOpt, maxHeapSizeOpt),
    Global / cancelable := false,
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % DeclineVersion,
      "com.github.pathikrit" %% "better-files" % "3.9.1"
    ) ++ Seq(
      "org.scalatest" %% "scalatest" % ScalaTestVersion,
      "org.scalatest" %% "scalatest-funspec" % FunSpecVersion
    ).map(_ % Test),
    // TODO remove
    Compile / scalacOptions ~= filterConsoleScalacOptions
  )
  // https://doc.akka.io/docs/akka/current/multi-jvm-testing.html?language=scala#multi-jvm-testing
  .configs(MultiJvm)
