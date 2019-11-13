val akkaVersion = "2.5.23"

lazy val root = (project in file("."))
  .settings(
    name := "akka-cluster-custom-downing",
    organization := "org.sisioh",
    sonatypeProfileName := "org.sisioh",
    homepage := Some(url("https://github.com/sisioh/akka-cluster-custom-downing")),
    scalaVersion := "2.13.1",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    scalacOptions ++= Seq(
        "-feature",
        "-deprecation",
        "-unchecked",
        "-encoding",
        "UTF-8",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-language:higherKinds"
      ),
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
        //  "com.typesafe.akka" %% "akka-cluster" % akkaVersion  % "test" classifier "tests",
        "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
        //  "com.typesafe.akka" %% "akka-testkit" % akkaVersion  % "test" classifier "tests",
        "org.scalatest" %% "scalatest" % "3.0.8" % Test
      ),
    compile in MultiJvm := (compile in MultiJvm).triggeredBy(compile in Test).value,
    parallelExecution in Test := false,
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
      }.value,
    assemblyMergeStrategy in (MultiJvm, assembly) := {
      case "application.conf" => MergeStrategy.concat
      case "META-INF/aop.xml" => MergeStrategy.concat
      case x =>
        val old = (assemblyMergeStrategy in (MultiJvm, assembly)).value
        old(x)
    },
    Test / fork := true,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra := {
      <url>https://github.com/sisioh/akka-cluster-custom-downing</url>
        <licenses>
          <license>
            <name>Apache-2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:sisioh/akka-cluster-custom-downing.git</url>
          <connection>scm:git:github.com/sisioh/akka-cluster-custom-downing</connection>
          <developerConnection>scm:git:git@github.com:sisioh/akka-cluster-custom-downing.git</developerConnection>
        </scm>
        <developers>
          <developer>
            <id>j5ik2o</id>
            <name>Junichi Kato</name>
          </developer>
        </developers>
    },
    publishTo in ThisBuild := sonatypePublishTo.value,
    credentials := {
      (
        sys.env.get("CREDENTIALS_REALM"),
        sys.env.get("CREDENTIALS_HOST"),
        sys.env.get("CREDENTIALS_USER_NAME"),
        sys.env.get("CREDENTIALS_PASSWORD")
      ) match {
        case (Some(r), Some(h), Some(u), Some(p)) =>
          Credentials(r, h, u, p) :: Nil
        case _ =>
          val ivyCredentials = (baseDirectory in LocalRootProject).value / ".credentials"
          Credentials(ivyCredentials) :: Nil
      }
    }
  )
  .configs(MultiJvm)
  .enablePlugins(MultiJvmPlugin, ReleasePlugin)
