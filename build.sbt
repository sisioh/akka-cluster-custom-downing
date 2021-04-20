import Dependencies._
import Dependencies.Versions._

def crossScalacOptions(scalaVersion: String): Seq[String] = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
    Seq.empty
  case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
    Seq("-Yinline-warnings")
}

lazy val baseSettings = Seq(
  organization := "org.sisioh",
  homepage := Some(url("https://github.com/sisioh/akka-cluster-custom-downing")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      id = "j5ik2o",
      name = "Junichi Kato",
      email = "j5ik2o@gmail.com",
      url = url("https://blog.j5ik2o.me")
    )
  ),
  scalaVersion := Versions.scala213Version,
  crossScalaVersions := Seq(Versions.scala211Version, Versions.scala212Version, Versions.scala213Version),
  scalacOptions ++= (
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:_",
      "-Ydelambdafy:method",
      "-target:jvm-1.8",
      "-Yrangepos",
      "-Ywarn-unused"
    ) ++ crossScalacOptions(scalaVersion.value)
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    "Seasar Repository" at "https://maven.seasar.org/maven2/",
    "DynamoDB Local Repository" at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
  ),
  ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / publishArtifact := false,
  Test / fork := true,
  Test / parallelExecution := false,
  envVars := Map(
    "AWS_REGION" -> "ap-northeast-1"
  )
)

lazy val root = (project in file("."))
  .settings(baseSettings)
  .settings(
    name := "akka-cluster-custom-downing",
    scalaVersion := scala213Version,
    crossScalaVersions := scala212Version :: scala213Version :: Nil,
    libraryDependencies ++= Seq(
      akka.actor(akka26Version),
      akka.cluster(akka26Version),
      akka.multiNodeTestkit(akka26Version)    % Test,
      akka.slf4j(akka26Version)               % Test,
      logback.classic                         % Test,
      scalatest.scalatest(scalaTest32Version) % Test
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => Nil
        case _ =>
          Seq("org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4")
      }
    },
    MultiJvm / compile := (MultiJvm / compile).triggeredBy(Test / compile).value,
    Test / executeTests := Def.task {
      val testResults      = (Test / executeTests).value
      val multiNodeResults = (MultiJvm / executeTests).value
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
    }.value,
    MultiJvm / assembly / assemblyMergeStrategy := {
      case "application.conf" => MergeStrategy.concat
      case "META-INF/aop.xml" => MergeStrategy.concat
      case x =>
        val old = (MultiJvm / assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
  .configs(MultiJvm)
  .enablePlugins(MultiJvmPlugin)
