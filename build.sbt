import Dependencies._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val neo4jDriverVersion = "1.6.3"
val shapelessVersion = "2.3.3"
val testcontainersScalaVersion = "0.20.0"
val mockitoVersion = "1.10.19"
val scalaTestVersion = "3.0.5"
val slf4jVersion = "1.7.21"

//lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val root = (project in file("."))
  .settings(
    organization in ThisBuild := "com.dimafeng",
    scalaVersion in ThisBuild := "2.12.2",
    crossScalaVersions := Seq("2.12.2"),
    name := "neotypes",
    //    compileScalastyle := scalastyle.in(Compile).toTask("").value,
    //    test in Test := (test in Test).dependsOn(compileScalastyle in Compile).value,

    /**
      * Dependencies
      */
    libraryDependencies ++=
      COMPILE(
        "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
        "com.chuusai" %% "shapeless" % shapelessVersion
      )
        ++ TEST(
        "org.scalatest" %% "scalatest" % scalaTestVersion,
        "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion,
        "org.mockito" % "mockito-all" % mockitoVersion,
        "org.slf4j" % "slf4j-simple" % slf4jVersion,
      ),

    parallelExecution in ThisBuild := false,

    /**
      * Publishing
      */
    useGpg := true,
    publishTo := sonatypePublishTo.value,
    publishMavenStyle := true,
    sonatypeProfileName := "neotypes",
    sonatypeProjectHosting := Some(GitLabHosting("neotypes", "neotypes", "dimafeng@gmail.com")),
    licenses := Seq("The MIT License (MIT)" -> new URL("https://opensource.org/licenses/MIT")),

    releaseCrossBuild := true,
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
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
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
    micrositeHomepage := "http://todo",
    micrositeDocumentationUrl := "docs.html",
    micrositeGithubOwner := "neotypes",
    micrositeGithubRepo := "neotypes",
    ghpagesNoJekyll := false,
    fork in tut := true
  )
