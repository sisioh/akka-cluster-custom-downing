name := "akka-cluster-custom-downing"

organization := "akka-cluster-custom-dowing"

homepage := Some(url("https://github.com/TanUkkii007/akka-cluster-custom-downing"))

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1")

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:higherKinds"
)

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

val akkaVersion = "2.5.23"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
//  "com.typesafe.akka" %% "akka-cluster" % akkaVersion  % "test" classifier "tests",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
//  "com.typesafe.akka" %% "akka-testkit" % akkaVersion  % "test" classifier "tests",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

compile in MultiJvm := (compile in MultiJvm).triggeredBy(compile in Test).value

parallelExecution in Test := false

executeTests in Test := Def.task {
  val testResults      = (executeTests in Test).value
  val multiNodeResults = (executeTests in MultiJvm).value
  val overall = (testResults.overall, multiNodeResults.overall) match {
    case (TestResult.Passed, TestResult.Passed) => TestResult.Passed
    case (TestResult.Error, _)                  => TestResult.Error
    case (_, TestResult.Error)                  => TestResult.Error
    case (TestResult.Failed, _)                 => TestResult.Failed
    case (_, TestResult.Failed)                 => TestResult.Failed
  }
  Tests.Output(
    overall,
    testResults.events ++ multiNodeResults.events,
    testResults.summaries ++ multiNodeResults.summaries
  )
}.value

assemblyMergeStrategy in (MultiJvm, assembly) := {
  case "application.conf" => MergeStrategy.concat
  case "META-INF/aop.xml" => MergeStrategy.concat
  case x =>
    val old = (assemblyMergeStrategy in (MultiJvm, assembly)).value
    old(x)
}

Test / fork := true

configs(MultiJvm)

BintrayPlugin.autoImport.bintrayPackage := "akka-cluster-custom-downing"

enablePlugins(MultiJvmPlugin, BintrayPlugin, ReleasePlugin)

releaseCrossBuild := true
