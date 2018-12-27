---
layout: home
position: 0
section: home
title: "Home"
---

![Logo](img/neotypes.png)

> neotype - a type specimen that is selected subsequent to the description of a species to replace a preexisting type that has been lost or destroyed

[![Build Status](https://travis-ci.org/neotypes/neotypes.svg?branch=master)](https://travis-ci.org/neotypes/neotypes)
[![Maven Central](https://img.shields.io/maven-central/v/com.dimafeng/neotypes_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.dimafeng/neotypes_2.12)
[![Gitter Chat](https://badges.gitter.im/neotypes-neotypes/Lobby.svg)](https://gitter.im/neotypes-neotypes/Lobby)

# neotypes

**Scala lightweight, type-safe, asynchronous driver (not opinionated on side-effect implementation) for neo4j**.

* **Scala** - the driver provides you with support for all standard Scala types without the need to convert Scala <-> Java types back and forth and you can easily add your types.
* **Lightweight** - the driver depends on `shapeless` and `neo4j Java driver`
* **Type-safe** - the driver leverages [typeclasses](https://blog.scalac.io/2017/04/19/typeclasses-in-scala.html) to derive all needed conversions at the compile time.
* **Asynchronous** - the driver sits on top of [asynchronous Java driver](https://neo4j.com/blog/beta-release-java-driver-async-api-neo4j/).
* **Not opinionated on side-effect implementation** - you can use it with any implementation of side-effects of your chose (scala.Future, cats-effect
 IO, Monix Task, etc) by implementing a simple typeclass. `scala.Future` is implemented and comes out of the box.

The project aims to provide seamless integration with most popular scala infrastructures such as lightbend (Akka, Akka-http, Lagom, etc), typelevel (cats, http4s, etc), twitter (finch, etc)...

See [Documentation](docs.html) for more details

## Setup

|||
| ----------------------------------------- |:--------------|
|`"com.dimafeng" %% "neotypes" % version`|Core functionality. Supports `scala.concurrent.Future`.|
|`"com.dimafeng" %% "neotypes-cats-effect" % version`| `cats.effect.Async[F]` implementation|
|`"com.dimafeng" %% "neotypes-monix" % version`| `monix.eval.Task` implementation|