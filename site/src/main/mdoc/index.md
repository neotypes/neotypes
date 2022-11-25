---
layout: home
section: home
title: "Home"
position: 0
---

![Logo](img/neotypes.png)

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

:warning: The library is under heavy development. Production use is at your own risk and is not recommended. :warning:

{:.table}
|Supports Scala 2.12 and 2.13||
| ----------------------------------------- | :------------- |
|`"io.github.neotypes" %% "neotypes-core" % version`|core functionality. Supports `scala.concurrent.Future`.|
|`"io.github.neotypes" %% "neotypes-generic" % version`|auto & semiauto derivation of mappers for case classes.|
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

### Showcase

```scala mdoc:compile-only
import neotypes.GraphDatabase
import neotypes.generic.auto._
import neotypes.implicits.syntax.all._
import org.neo4j.driver.AuthTokens
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val people = "MATCH (p: Person) RETURN p.name, p.born LIMIT 10".readOnlyQuery[(String, Int)].list(driver)
Await.result(people, 1.second)
// res: Seq[(String, Int)] = ArrayBuffer(
//   (Charlize Theron, 1975),
//   (Keanu Reeves, 1964),
//   (Carrie-Anne Moss, 1967),
//   (Laurence Fishburne, 1961),
//   (Hugo Weaving, 1960),
//   (Lilly Wachowski, 1967),
//   (Lana Wachowski, 1965),
//   (Joel Silver,1952),
//   (Emil Eifrem,1978),
//   (Charlize Theron,1975)
// )

final case class Person(id: Long, born: Int, name: Option[String], notExists: Option[Int])

val peopleCC = "MATCH (p: Person) RETURN p LIMIT 10".readOnlyQuery[Person].list(driver)
Await.result(peopleCC, 1.second)
// res: Seq[Person] = ArrayBuffer(
//   Person(0, 1975, Some(Charlize Theron), None),
//   Person(1, 1964, Some(Keanu Reeves), None),
//   Person(2, 1967, Some(Carrie-Anne Moss), None),
//   Person(3, 1961, Some(Laurence Fishburne), None),
//   Person(4, 1960, Some(Hugo Weaving), None),
//   Person(5, 1967, Some(Lilly Wachowski), None),
//   Person(6, 1965, Some(Lana Wachowski), None),
//   Person(7, 1952, Some(Joel Silver), None),
//   Person(8, 1978, Some(Emil Eifrem), None),
//   Person(9, 1975, Some(Charlize Theron), None)
// )

Await.ready(driver.close, 1.second)
```

## Compatibility matrix

| Java driver version | Neotypes version |
| :-----------------: | :--------------: |
| 5.y.x               | >= 0.23          |
| 4.y.x               | >= 0.14          |
| 1.7.x               | <= 0.13          |

For info on the compatibility with **Java** runtimes or **Neo4j** servers,
please check the the [**Java** driver docs](https://github.com/neo4j/neo4j-java-driver).

> Note: Since `0.23.0` the artifacts are published for **Java 17** only,
> since that is what the `5.x` series of the **Java** driver requires.<br>
> Note: Since `0.23.1` the minimum supported version of the **Java** driver is `5.3.0`
