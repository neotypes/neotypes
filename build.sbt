import Dependencies._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val neo4jDriverVersion = "4.0.1"
val scalaCollectionCompatVersion = "2.1.3"
val shapelessVersion = "2.3.3"
val testcontainersScalaVersion = "0.34.3"
val mockitoVersion = "1.10.19"
val scalaTestVersion = "3.0.8"
val slf4jVersion = "1.7.30"
val catsVersion = "2.1.0"
val catsEffectsVersion = "2.0.0"
val monixVersion = "3.1.0"
val akkaStreamVersion = "2.6.1"
val fs2Version = "2.1.0"
val zioVersion = "1.0.0-RC17"
val refinedVersion = "0.9.10"

//lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

val commonSettings = Seq(
  scalaVersion in ThisBuild := "2.12.10",
  crossScalaVersions := Seq("2.13.1", "2.12.10"),
  scalacOptions += "-Ywarn-macros:after",
  scalacOptions in Test := Seq("-feature", "-deprecation"),
  autoAPIMappings := true,

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

  parallelExecution in Global := false,

  releaseCrossBuild := true
)

lazy val noPublishSettings = Seq(
  skip in publish := true
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
    catsData
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
    name := "neotypes",
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
        "org.mockito" % "mockito-all" % mockitoVersion,
        "org.slf4j" % "slf4j-simple" % slf4jVersion
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
    scalacOptions in Test ++= enablePartialUnificationIn2_12(scalaVersion.value),
    libraryDependencies ++= PROVIDED(
      "org.typelevel" %% "cats-effect" % catsEffectsVersion
    )
  )

lazy val monix = (project in file("monix"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix",
    libraryDependencies ++= PROVIDED(
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
      "org.typelevel" %% "cats-effect" % catsEffectsVersion,
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )

lazy val monixStream = (project in file("monix-stream"))
  .dependsOn(core % "compile->compile;test->test;provided->provided")
  .dependsOn(monix % "test->test")
  .settings(commonSettings)
  .settings(
    name := "neotypes-monix-stream",
    libraryDependencies ++= PROVIDED(
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
      "dev.zio" %% "zio-streams" % zioVersion
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
    micrositeGithubOwner := "neotypes",
    micrositeGithubRepo := "neotypes",
    micrositeBaseUrl := "/neotypes",
    ghpagesNoJekyll := false,
    mdocIn := (sourceDirectory in Compile).value / "mdoc",
    fork in mdoc := true,
    docsMappingsAPIDir := "api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), docsMappingsAPIDir),
    micrositeDocumentationLabelDescription := "API Documentation",
    micrositeDocumentationUrl := "/neotypes/api/neotypes/index.html",
    mdocExtraArguments := Seq("--no-link-hygiene"),
    scalacOptions in Compile -= "-Xfatal-warnings",
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
      "-groups",
      "-doc-source-url",
      scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
      "-sourcepath",
      baseDirectory.in(LocalRootProject).value.getAbsolutePath,
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
    zioStream % "compile->compile;provided->provided"
  )

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")
