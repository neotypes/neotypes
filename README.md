![Logo](site/src/main/resources/microsite/img/neotypes.png)

> neotype - a type specimen that is selected subsequent to the description of a species to replace a preexisting type that has been lost or destroyed.

[![Build status](https://github.com/neotypes/neotypes/workflows/CI/badge.svg?branch=main)](https://github.com/neotypes/neotypes/actions)
[![Scaladex](https://index.scala-lang.org/neotypes/neotypes/neotypes-core/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/neotypes/neotypes/neotypes-core)
[![Gitter chat](https://badges.gitter.im/neotypes-neotypes/Lobby.svg)](https://gitter.im/neotypes-neotypes/Lobby)
[![Scala Steward](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)


# neotypes

:warning: The library is under heavy development. Production use is at your own risk and is not recommended. :warning:

For early adopters:

|Supports Scala 2.12 and 2.13||
| ----------------------------------------- |:--------------|
|`"io.github.neotypes" %% "neotypes-core" % version`|core functionality. Supports `scala.concurrent.Future`.|
|`"io.github.neotypes" %% "neotypes-cats-effect" % version`|`cats.effect.Async[F]` implementation.|
|`"io.github.neotypes" %% "neotypes-monix" % version`|`monix.eval.Task` implementation.|
|`"io.github.neotypes" %% "neotypes-zio" % version`|`zio.Task` implementation.|
|`"io.github.neotypes" %% "neotypes-akka-stream" % version`|result streaming for Akka Streams.|
|`"io.github.neotypes" %% "neotypes-fs2-stream" % version`|result streaming for FS2.|
|`"io.github.neotypes" %% "neotypes-monix-stream" % version`|result streaming for Monix Observables.|
|`"io.github.neotypes" %% "neotypes-zio-stream" % version`|result streaming for ZIO ZStreams.|
|`"io.github.neotypes" %% "neotypes-refined" % version`|support to insert and retrieve refined values.|
|`"io.github.neotypes" %% "neotypes-cats-data" % version`|support to insert and retrieve `cats.data` values.|
|`"io.github.neotypes" %% "neotypes-enumeratum" % version`|support to insert and retrieve Enumeratum enums.|

**Scala lightweight, type-safe, asynchronous driver (not opinionated on side-effect implementation) for neo4j**.

* **Scala** - the driver provides you with support for all standard Scala types without the need to convert Scala <-> Java types back and forth and you can easily add your types.
* **Lightweight** - the driver depends on `shapeless` and `neo4j Java driver`.
* **Type-safe** - the driver leverages [typeclasses](https://blog.scalac.io/2017/04/19/typeclasses-in-scala.html) to derive all needed conversions at the compile time.
* **Asynchronous** - the driver sits on top of [asynchronous Java driver](https://neo4j.com/blog/beta-release-java-driver-async-api-neo4j/).
* **Not opinionated on side-effect implementation** - you can use it with any implementation of side-effects of your chose (scala.Future, cats-effect
 IO, Monix Task, etc) by implementing a simple typeclass. `scala.Future` is implemented and comes out of the box.

The project aims to provide seamless integration with most popular scala infrastructures such as lightbend (Akka, Akka-http, Lagom, etc), typelevel (cats, http4s, etc), twitter (finch, etc)...

## Resources

* [Documentation](https://neotypes.github.io/neotypes)
* [Example project (Akka-http + neotypes)](https://github.com/neotypes/examples)

## Code of Conduct

We are committed to providing a friendly, safe and welcoming environment for all, regardless of level of experience, gender, gender identity and expression, sexual orientation, disability, personal appearance, body size, race, ethnicity, age, religion, nationality, or other such characteristics.

Everyone is expected to follow the [Scala Code of Conduct](https://www.scala-lang.org/conduct/) when discussing the project on the available communication channels.

## Special thanks

* [Luis Miguel Mejía Suárez](https://github.com/BalmungSan)
* [geoffjohn11](https://github.com/geoffjohn11)
