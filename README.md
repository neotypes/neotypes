![Logo](microsite/src/main/resources/microsite/img/neotypes.png)

> neotype - a type specimen that is selected subsequent to the description of a species to replace a preexisting type that has been lost or destroyed.

[![Build status](https://github.com/neotypes/neotypes/workflows/CI/badge.svg?branch=main)](https://github.com/neotypes/neotypes/actions)
[![Scaladex](https://index.scala-lang.org/neotypes/neotypes/neotypes-core/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/neotypes/neotypes/neotypes-core)
[![Gitter chat](https://badges.gitter.im/neotypes-neotypes/Lobby.svg)](https://gitter.im/neotypes-neotypes/Lobby)
[![Scala Steward](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

# neotypes

## Scala lightweight, type-safe, asynchronous driver _(not opinionated on effect systems)_ for Neo4j

* **Scala** - the driver provides you with support for all standard **Scala** types without the need to convert **Scala** <-> **Java** types back and forth and you can easily add support for your own types.
* **Lightweight** - the `core` module of the driver only depends the **Neo4j Java driver**, and the `generic` module only depends on **Shapeless**.
* **Type-safe** - the driver leverages [typeclasses](https://blog.scalac.io/2017/04/19/typeclasses-in-scala.html) to derive all needed conversions at the compile time.
* **Asynchronous** - the driver sits on top of [asynchronous **Java** driver](https://neo4j.com/blog/beta-release-java-driver-async-api-neo4j/).
* **Not opinionated on side-effect implementation** - you can use it with any effect system of your preference _(`Future`, **typelevel**, **ZIO**, **Monix**)_ by implementing a simple typeclass.

### Setup

| Supports Scala 2.13 and 3.3                                 |                                                                |
|-------------------------------------------------------------|:---------------------------------------------------------------|
| `"io.github.neotypes" %% "neotypes-core" % version`         | Core functionality. Supports `scala.concurrent.Future`.        |
| `"io.github.neotypes" %% "neotypes-generic" % version`      | Automatic derivation of mappers for case classes.              |
| `"io.github.neotypes" %% "neotypes-cats-effect" % version`  | `Async` support for `cats.effect.Async[F]`                     |
| `"io.github.neotypes" %% "neotypes-monix" % version`        | `Async` support for `monix.eval.Task`                          |
| `"io.github.neotypes" %% "neotypes-zio" % version`          | `Async` support for `zio.Task`                                 |
| `"io.github.neotypes" %% "neotypes-akka-stream" % version`  | `Stream` support for `akka.stream.scaladsl.Source`             |
| `"io.github.neotypes" %% "neotypes-pekko-stream" % version` | `Stream` support for `org.apache.pekko.stream.scaladsl.Source` |
| `"io.github.neotypes" %% "neotypes-fs2-stream" % version`   | `Stream` support for `fs2.Stream`                              |
| `"io.github.neotypes" %% "neotypes-monix-stream" % version` | `Stream` support for `monix.reactive.Observable`               |
| `"io.github.neotypes" %% "neotypes-zio-stream" % version`   | `Stream` support for `zio.ZStream`                             |
| `"io.github.neotypes" %% "neotypes-refined" % version`      | Support for insert and retrieve refined values.                |
| `"io.github.neotypes" %% "neotypes-cats-data" % version`    | Support for insert and retrieve `cats.data` values.            |
| `"io.github.neotypes" %% "neotypes-enumeratum" % version`   | Support for insert and retrieve Enumeratum enums.              |

### Resources

* [Documentation](https://neotypes.github.io/neotypes)
* [Example project (Akka-http + neotypes)](https://github.com/neotypes/examples)

### Code of Conduct

We are committed to providing a friendly, safe and welcoming environment for all, regardless of level of experience, gender, gender identity and expression, sexual orientation, disability, personal appearance, body size, race, ethnicity, age, religion, nationality, or other such characteristics.

Everyone is expected to follow the [Scala Code of Conduct](https://www.scala-lang.org/conduct/) when discussing the project on the available communication channels.

### Special thanks

* [Luis Miguel Mejía Suárez](https://github.com/BalmungSan)
* [geoffjohn11](https://github.com/geoffjohn11)
