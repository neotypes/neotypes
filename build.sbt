import Dependencies.*

import ReleaseTransformations.*

import java.net.URI

val scala213 = "2.13.16"
val scala3 = "3.3.6"
val neo4jDriverVersion = "5.28.9"
val shapelessVersion = "2.3.13"
val shapeless3Version = "3.5.0"
val testcontainersNeo4jVersion = "1.21.3"
val testcontainersScalaVersion = "0.43.0"
val scalaTestVersion = "3.2.19"
val logbackVersion = "1.5.18"
val catsVersion = "2.13.0"
val catsEffect2Version = "2.5.5"
val catsEffect3Version = "3.6.3"
val monixVersion = "3.4.1"
val akkaStreamVersion = "2.6.20"
val pekkoStreamVersion = "1.1.4"
val fs2Version = "3.12.0"
val zio2Version = "2.1.20"
val zioInteropReactiveStreamsVersion = "2.0.2"
val refinedVersion = "0.11.3"
val enumeratumVersion = "1.9.0"

// Fix scmInfo in Github Actions.
ThisBuild / scmInfo ~= {
  case Some(info) =>
    Some(info)

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
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq(scala213, scala3)

ThisBuild / versionScheme := Some("semver-spec")

ThisBuild / organization := "io.github.neotypes"
ThisBuild / organizationName := "neotypes"
ThisBuild / homepage := Some(url("https://neotypes.github.io/neotypes/"))
ThisBuild / apiURL := Some(url("https://neotypes.github.io/neotypes/api/neotypes/index.html"))
ThisBuild / startYear := Some(2018)
ThisBuild / licenses += "The MIT License (MIT)" -> URI.create("https://opensource.org/licenses/MIT").toURL

ThisBuild / developers := List(
  Developer(
    "BalmungSan",
    "Luis Miguel Mejía Suárez",
    "luismiguelmejiasuarez@gmail.com",
    url("https://github.com/BalmungSan")
  ),
  Developer(
    "dimafeng",
    "Dmitry Fedosov",
    "dimafeng@gmail.com",
    url("https://github.com/dimafeng")
  )
)

// Common settings.
lazy val commonSettings = Def.settings(
  // Ensure we publish an artifact linked to the appropriate Java std library.
  scalacOptions += "-release:17",
  scalacOptions ++= (
    if (scalaVersion.value.startsWith("2.13."))
      Seq(
        // Run the compiler linter after macros have expanded.
        "-Ywarn-macros:after",
        // Implicit resolution debug flags.
        "-Vimplicits",
        "-Vtype-diffs",
        "-Ytasty-reader",
        // Make all warnings verbose.
        "-Wconf:any:warning-verbose"
      )
    else if (scalaVersion.value.startsWith("3.3."))
      Seq(
        // Make all warnings verbose.
        "-Wconf:any:verbose"
      )
    else
      Seq.empty
  ),
  // Publishing.
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },
  releaseCrossBuild := true
)

lazy val noPublishSettings = Seq(
  publish / skip := true
)

lazy val root = (project in file("."))
  .aggregate(
    core,
    generic,
    catsEffect,
    monix,
    zio,
    akkaStream,
    pekkoStream,
    fs2Stream,
    monixStream,
    zioStream,
    refined,
    catsData,
    enumeratum,
    tests
  )
  .settings(noPublishSettings)
  .settings(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepTaskAggregated(Test / compile),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepCommandAndRemaining("sonaRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "neotypes-core",
    Compile / sourceGenerators += Boilerplate.generatorTask.taskValue,
    libraryDependencies ++=
      PROVIDED(
        "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion
      ),
    libraryDependencies ++= (
      if (scalaVersion.value.startsWith("2.13."))
        COMPILE(
          scalaVersion("org.scala-lang" % "scala-reflect" % _).value
        )
      else Seq.empty
    )
  )

lazy val generic = (project in file("generic"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-generic",
    libraryDependencies += (
      if (scalaVersion.value.startsWith("2.13."))
        "com.chuusai" %% "shapeless" % shapelessVersion
      else
        "org.typelevel" %% "shapeless3-deriving" % shapeless3Version
    )
  )

lazy val catsEffect = (project in file("cats-effect"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-cats-effect",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffect3Version
    )
  )

lazy val monix = (project in file("monix"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffect2Version,
      "io.monix" %% "monix-eval" % monixVersion
    )
  )

lazy val zio = (project in file("zio"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-zio",
    libraryDependencies ++= PROVIDED(
      "dev.zio" %% "zio" % zio2Version
    )
  )

lazy val akkaStream = (project in file("akka-stream"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-akka-stream",
    libraryDependencies ++= PROVIDED(
      "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion
    )
  )

lazy val pekkoStream = (project in file("pekko-stream"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-pekko-stream",
    libraryDependencies ++= PROVIDED(
      "org.apache.pekko" %% "pekko-stream" % pekkoStreamVersion
    )
  )

lazy val fs2Stream = (project in file("fs2-stream"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-fs2-stream",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffect3Version,
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-reactive-streams" % fs2Version
    )
  )

lazy val monixStream = (project in file("monix-stream"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix-stream",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffect2Version,
      "io.monix" %% "monix-eval" % monixVersion,
      "io.monix" %% "monix-reactive" % monixVersion
    )
  )

lazy val zioStream = (project in file("zio-stream"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-zio-stream",
    libraryDependencies ++= PROVIDED(
      "dev.zio" %% "zio" % zio2Version,
      "dev.zio" %% "zio-streams" % zio2Version,
      "dev.zio" %% "zio-interop-reactivestreams" % zioInteropReactiveStreamsVersion
    )
  )

lazy val catsData = (project in file("cats-data"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-cats-data",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-core" % catsVersion
    )
  )

lazy val refined = (project in file("refined"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-refined",
    libraryDependencies ++= PROVIDED(
      "eu.timepit" %% "refined" % refinedVersion
    )
  )

lazy val enumeratum = (project in file("enumeratum"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "neotypes-enumeratum",
    libraryDependencies ++= PROVIDED(
      "com.beachape" %% "enumeratum" % enumeratumVersion
    )
  )

lazy val tests = (project in file("tests"))
  .dependsOn(
    core % "compile->compile;provided->provided",
    generic % "compile->compile;provided->provided",
    catsEffect % "compile->compile;provided->provided",
    zio % "compile->compile;provided->provided",
    akkaStream % "compile->compile;provided->provided",
    pekkoStream % "compile->compile;provided->provided",
    fs2Stream % "compile->compile;provided->provided",
    zioStream % "compile->compile;provided->provided",
    catsData % "compile->compile;provided->provided",
    refined % "compile->compile;provided->provided",
    enumeratum % "compile->compile;provided->provided"
  )
  .settings(commonSettings, noPublishSettings)
  .settings(
    scalacOptions ++= (
      if (scalaVersion.value.startsWith("3.3."))
        Seq(
          "-Yretain-trees"
        )
      else
        Seq.empty
    ),
    libraryDependencies ++=
      TEST(
        "org.scalatest" %% "scalatest" % scalaTestVersion,
        "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion,
        "com.dimafeng" %% "testcontainers-scala-neo4j" % testcontainersScalaVersion,
        "org.testcontainers" % "neo4j" % testcontainersNeo4jVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion
      ),
    // Disable Wnonunit-statement warnings related to ScalaTest Assertion.
    Test / scalacOptions ++= (
      if (scalaVersion.value.startsWith("2.13."))
        Seq(
          "-Wconf:cat=other-pure-statement&msg=Assertion:s"
        )
      else if (scalaVersion.value.startsWith("3.3."))
        Seq(
          "-Wconf:name=UnusedNonUnitValue&msg=Assertion:s"
        )
      else
        Seq.empty
    ),
    // Fork tests and disable parallel execution to avoid issues with Docker.
    Test / fork := true,
    Test / parallelExecution := false,
    // Print full stack traces of failed tests and a remainder of failed tests:
    // https://www.scalatest.org/user_guide/using_scalatest_with_sbt
    Test / testOptions += Tests.Argument("-oFIK")
  )

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")

lazy val microsite = (project in file("microsite"))
  .dependsOn(
    core % "compile->compile;provided->provided",
    generic % "compile->compile;provided->provided",
    catsEffect % "compile->compile;provided->provided",
    zio % "compile->compile;provided->provided",
    akkaStream % "compile->compile;provided->provided",
    pekkoStream % "compile->compile;provided->provided",
    fs2Stream % "compile->compile;provided->provided",
    zioStream % "compile->compile;provided->provided",
    catsData % "compile->compile;provided->provided",
    refined % "compile->compile;provided->provided",
    enumeratum % "compile->compile;provided->provided"
  )
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin)
  .settings(commonSettings, noPublishSettings)
  .settings(
    Compile / scalacOptions -= "-Xfatal-warnings",
    libraryDependencies += "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
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
    ScalaUnidoc / unidoc / scalacOptions ++= Seq(
      "-groups",
      "-doc-source-url",
      scmInfo.value.get.browseUrl + "/tree/main€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-diagrams"
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(monix, monixStream)
  )
