val scala212Version = "2.12.10"
val scala213Version = "2.13.1"
val akkaVersion     = "2.6.12"

lazy val root = (project in file("."))
  .settings(
    name := "akka-cluster-custom-downing",
    organization := "org.sisioh",
    organizationHomepage := Some(url("https://github.com/sisioh")),
    sonatypeProfileName := "org.sisioh",
    scalaVersion := scala213Version,
    crossScalaVersions := scala212Version :: scala213Version :: Nil,
    scalacOptions ++=
      Seq(
        "-feature",
        "-deprecation",
        "-unchecked",
        "-encoding",
        "UTF-8",
        "-language:_",
        "-target:jvm-1.8"
      ),
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor"              % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster"            % akkaVersion,
        "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
        "com.typesafe.akka" %% "akka-slf4j"              % akkaVersion % Test,
        "ch.qos.logback"    % "logback-classic"          % "1.2.3" % Test,
        "org.scalatest"     %% "scalatest"               % "3.1.2" % Test,
        "org.scalactic"     %% "scalactic"               % "3.1.4" % Test
      ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => Nil
        case _ =>
          Seq("org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4")
      }
    },
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
    licenses := Seq(
        "Apache-2.0" -> url("https://raw.githubusercontent.com/sisioh/akka-cluster-custom-downing/master/LICENSE")
      ),
    homepage := scmInfo.value map (_.browseUrl),
    scmInfo := Some(
        ScmInfo(
          browseUrl = url("https://github.com/sisioh/akka-cluster-custom-downing"),
          connection = "scm:git:git@github.com:sisioh/akka-cluster-custom-downing.git"
        )
      ),
    developers := List(
        Developer(
          id = "j5ik2o",
          name = "Junichi Kato",
          email = "j5ik2o@gmail.com",
          url = url("https://blog.j5ik2o.me")
        )
      ),
    publishTo := sonatypePublishToBundle.value,
    credentials := {
      val ivyCredentials = (baseDirectory in LocalRootProject).value / ".credentials"
      val gpgCredentials = (baseDirectory in LocalRootProject).value / ".gpgCredentials"
      Credentials(ivyCredentials) :: Credentials(gpgCredentials) :: Nil
    }
  )
  .configs(MultiJvm)
  .enablePlugins(MultiJvmPlugin, ReleasePlugin)
