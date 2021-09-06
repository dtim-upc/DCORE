import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val LibraryVersion = "0.1.0"
val ScalaVersion = "2.12.14"

val AkkaVersion = "2.6.16"
val ScalaTestVersion = "3.1.4"
val BetterMonadForVersion = "0.3.1"

val LibraryDependencies =
  Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

val TestDependencies =
  Seq(
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion,
    "org.scalatest" %% "scalatest-funspec" % "3.2.9"
  ).map(_ % Test)

lazy val root = (project in file("."))
  .enablePlugins(
    // https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html
    JavaAppPackaging
  )
  .settings(multiJvmSettings: _*)
  .settings(
    name := "DistributedCER",
    version := LibraryVersion,
    scalaVersion := ScalaVersion,
//    run / javaOptions ++= Seq(
//      "-Xms128m",
//      "-Xmx1024m"
//    ),
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
