import Dependencies._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val neo4jDriverVersion = "1.7.3"
val shapelessVersion = "2.3.3"
val testcontainersScalaVersion = "0.23.0"
val mockitoVersion = "1.10.19"
val scalaTestVersion = "3.0.5"
val slf4jVersion = "1.7.21"
val catsEffectsVersion = "1.2.0"
val monixVersion = "3.0.0-RC2"
val akkaStreamVersion = "2.5.19"

//lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

val commonSettings = Seq(
  scalaVersion in ThisBuild := "2.11.12",
  crossScalaVersions := Seq("2.12.8", "2.11.12"),

  /**
    * Publishing
    */
  useGpg := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  sonatypeProfileName := "neotypes",
  sonatypeProjectHosting := Some(GitLabHosting("neotypes", "neotypes", "dimafeng@gmail.com")),
  licenses := Seq("The MIT License (MIT)" -> new URL("https://opensource.org/licenses/MIT")),
  organization in ThisBuild := "com.dimafeng",

  releaseCrossBuild := true,

  parallelExecution in ThisBuild := false
)

lazy val noPublishSettings = Seq(
  skip in publish := true
)

lazy val root = (project in file("."))
  .aggregate(
    core,
    catsEffect,
    monix,
    akkaStream
  )
  .settings(noPublishSettings)
  .settings(

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
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
  .settings(commonSettings: _*)
  .settings(
    name := "neotypes",

    libraryDependencies ++=
      PROVIDED(
        "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion
      )
        ++ COMPILE(
        "com.chuusai" %% "shapeless" % shapelessVersion
      )
        ++ TEST(
        "org.scalatest" %% "scalatest" % scalaTestVersion,
        "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion,
        "org.mockito" % "mockito-all" % mockitoVersion,
        "org.slf4j" % "slf4j-simple" % slf4jVersion
      )
  )

lazy val catsEffect = (project in file("cats-effect"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings: _*)
  .settings(
    name := "neotypes-cats-effect",
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-effect" % catsEffectsVersion
    )
  )

lazy val monix = (project in file("monix"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings: _*)
  .settings(
    name := "neotypes-monix",
    libraryDependencies ++= PROVIDED(
      "io.monix" %% "monix" % monixVersion
    )
  )

lazy val akkaStream = (project in file("akka-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings: _*)
  .settings(
    name := "neotypes-akka-stream",
    libraryDependencies ++= PROVIDED(
      "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion
    )
  )

lazy val microsite = (project in file("docs"))
  .settings(moduleName := "docs")
  .enablePlugins(MicrositesPlugin)
  .settings(
    micrositeName := "neotypes",
    micrositeDescription := "Scala lightweight, type-safe, asynchronous driver for neo4j",
    micrositeAuthor := "dimafeng",
    micrositeHighlightTheme := "atom-one-light",
    micrositeHomepage := "https://neotypes.github.io/neotypes/",
    micrositeDocumentationUrl := "docs.html",
    micrositeGithubOwner := "neotypes",
    micrositeGithubRepo := "neotypes",
    micrositeBaseUrl := "/neotypes",
    ghpagesNoJekyll := false,
    fork in tut := true
  )
