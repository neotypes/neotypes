import Dependencies._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val neo4jDriverVersion = "4.3.4"
val scalaCollectionCompatVersion = "2.5.0"
val shapelessVersion = "2.3.7"
val testcontainersNeo4jVersion = "1.16.2"
val testcontainersScalaVersion = "0.39.9"
val mockitoVersion = "1.10.19"
val scalaTestVersion = "3.2.10"
val logbackVersion = "1.2.6"
val catsVersion = "2.6.1"
val catsEffectsVersion = "2.5.4"
val monixVersion = "3.4.0"
val akkaStreamVersion = "2.6.16"
val fs2Version = "2.5.9"
val zioVersion = "1.0.12"
val zioInteropReactiveStreamsVersion = "1.3.7"
val refinedVersion = "0.9.27"
val enumeratumVersion = "1.7.0"

// Fix scmInfo in Github Actions.
// See: https://github.com/sbt/sbt-git/issues/171
ThisBuild / scmInfo ~= {
  case Some(info) => Some(info)
  case None =>
    import scala.sys.process._
    import scala.util.control.NonFatal
    val identifier = """([^\/]+)"""
    val GitHubHttps = s"https://github.com/$identifier/$identifier".r
    try {
      val remote = List("git", "ls-remote", "--get-url", "origin").!!.trim()
      remote match {
        case GitHubHttps(user, repo) =>
          Some(
            ScmInfo(
              url(s"https://github.com/$user/$repo"),
              s"scm:git:https://github.com/$user/$repo.git",
              Some(s"scm:git:git@github.com:$user/$repo.git")
            )
          )
        case _ =>
          None
      }
    } catch {
      case NonFatal(_) => None
    }
  }

// Global settings.
ThisBuild / scalaVersion := "2.12.15"
ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.7")
ThisBuild / organization := "io.github.neotypes"
ThisBuild / versionScheme := Some("semver-spec")

def removeScalacOptionsInTest(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) => Seq("-Ywarn-value-discard", "-Ywarn-unused:params")
    case _ => Seq("-Wvalue-discard", "-Wunused:explicits", "-Wunused:params", "-Wunused:imports")
  }

// Common settings.
val commonSettings = Seq(
  scalacOptions += "-Ywarn-macros:after",
  Test / parallelExecution := false,
  Test / fork := true,
  Test / scalacOptions --= removeScalacOptionsInTest(scalaVersion.value),

  /**
    * Publishing
    */
  publishTo := {
    val nexus = "https://s01.oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  sonatypeProfileName := "neotypes",
  sonatypeProjectHosting := Some(GitLabHosting("neotypes", "neotypes", "dimafeng@gmail.com")),
  licenses := Seq("The MIT License (MIT)" -> new URL("https://opensource.org/licenses/MIT")),

  releaseCrossBuild := true
)

lazy val noPublishSettings = Seq(
  publish / skip := true
)

lazy val root = (project in file("."))
  .aggregate(
    core,
    catsEffect,
    monix,
    zio,
    akkaStream,
    fs2Stream,
    monixStream,
    zioStream,
    refined,
    catsData,
    enumeratum
  )
  .settings(noPublishSettings)
  .settings(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      //runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      //releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )
  )

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "neotypes-core",
    libraryDependencies ++=
      PROVIDED(
        "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion
      ) ++ COMPILE(
        "com.chuusai" %% "shapeless" % shapelessVersion,
        "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion,
        scalaVersion("org.scala-lang" % "scala-reflect" % _).value
      ) ++ TEST(
        "org.scalatest" %% "scalatest" % scalaTestVersion,
        "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion,
        "com.dimafeng" %% "testcontainers-scala-neo4j" % testcontainersScalaVersion,
        "org.testcontainers" % "neo4j" % testcontainersNeo4jVersion,
        "org.mockito" % "mockito-all" % mockitoVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion
      )
  )

def enablePartialUnificationIn2_12(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) => Seq("-Ypartial-unification")
    case _ => Seq()
  }

lazy val catsEffect = (project in file("cats-effect"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-cats-effect",
    Test / scalacOptions ++= enablePartialUnificationIn2_12(scalaVersion.value),
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectsVersion
    )
  )

lazy val monix = (project in file("monix"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectsVersion,
      "io.monix" %% "monix-eval" % monixVersion
    )
  )

lazy val zio = (project in file("zio"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-zio",
    libraryDependencies ++= PROVIDED(
      "dev.zio" %% "zio" % zioVersion
    )
  )

lazy val akkaStream = (project in file("akka-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-akka-stream",
    libraryDependencies ++= PROVIDED(
      "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion
    )
  )

lazy val fs2Stream = (project in file("fs2-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .dependsOn(catsEffect % "test->test")
  .settings(commonSettings)
  .settings(
    name := "neotypes-fs2-stream",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectsVersion,
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-reactive-streams" % fs2Version
    )
  )

lazy val monixStream = (project in file("monix-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .dependsOn(monix % "test->test")
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix-stream",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectsVersion,
      "io.monix" %% "monix-eval" % monixVersion,
      "io.monix" %% "monix-reactive" % monixVersion
    )
  )

lazy val zioStream = (project in file("zio-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .dependsOn(zio % "test->test")
  .settings(commonSettings)
  .settings(
    name := "neotypes-zio-stream",
    libraryDependencies ++= PROVIDED(
      "dev.zio" %% "zio"         % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-interop-reactivestreams" % zioInteropReactiveStreamsVersion
    )
  )

lazy val refined = (project in file("refined"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-refined",
    libraryDependencies ++= PROVIDED(
      "eu.timepit" %% "refined" % refinedVersion
    )
  )

lazy val catsData = (project in file("cats-data"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-cats-data",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion
    )
  )

lazy val enumeratum = (project in file("enumeratum"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-enumeratum",
    libraryDependencies ++= PROVIDED(
      "com.beachape" %% "enumeratum" % enumeratumVersion
    )
  )

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")

lazy val microsite = (project in file("site"))
  .settings(moduleName := "site")
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin)
  .settings(
    micrositeName := "neotypes",
    micrositeDescription := "Scala lightweight, type-safe, asynchronous driver for neo4j",
    micrositeAuthor := "neotypes",
    micrositeTheme := "pattern",
    micrositeHighlightTheme := "atom-one-light",
    micrositeHomepage := "https://neotypes.github.io/neotypes/",
    micrositeDocumentationUrl := "docs.html",
    micrositeHomeButtonTarget := "repo",
    micrositeSearchEnabled := true,
    micrositeGithubOwner := "neotypes",
    micrositeGithubRepo := "neotypes",
    micrositeBaseUrl := "/neotypes",
    ghpagesNoJekyll := false,
    mdocIn := (Compile / sourceDirectory).value / "mdoc",
    autoAPIMappings := true,
    docsMappingsAPIDir := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, docsMappingsAPIDir),
    micrositeDocumentationLabelDescription := "API Documentation",
    micrositeDocumentationUrl := "/neotypes/api/neotypes/index.html",
    mdocExtraArguments := Seq("--no-link-hygiene"),
    Compile / scalacOptions -= "-Xfatal-warnings",
    ScalaUnidoc / unidoc / scalacOptions ++= Seq(
      "-groups",
      "-doc-source-url",
      scmInfo.value.get.browseUrl + "/tree/main€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-diagrams"
    ),
    libraryDependencies += "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion
  ).dependsOn(
    core % "compile->compile;provided->provided",
    catsEffect % "compile->compile;provided->provided",
    monix % "compile->compile;provided->provided",
    zio % "compile->compile;provided->provided",
    akkaStream % "compile->compile;provided->provided",
    fs2Stream % "compile->compile;provided->provided",
    monixStream % "compile->compile;provided->provided",
    zioStream % "compile->compile;provided->provided",
    catsData % "compile->compile;provided->provided",
    refined % "compile->compile;provided->provided",
    enumeratum % "compile->compile;provided->provided"
  )
