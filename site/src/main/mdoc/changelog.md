---
layout: page
section: changelog
title: "Changelog"
position: 100
---

# Changelog

## v0.16.0 _(2020-11-??)_

### Add wrappers for the new Rx module ([#164](https://github.com/neotypes/neotypes/pull/164){:target="_blank"})

We replaced our in-house implementation of **Streaming**
with wrappers for the new `Rx` module of the **Java** driver.

This change implies that now you can chose between a normal `Session[F]` or a `StreamingSession[F, S]`.
The first one no longer supports streaming data from the database,
but the second one implies that even no-streaming operations like `single`
are implemented in terms of `ReactiveStreams`.

This is quite a big change, because now the `Stream` typeclass
doesn't need to be in scope when calling `stream`,
but rather when creating the **Session**;
By calling `Driver[F[_]]#streamingSession[S[_]]`
_(which instead of returning a **Resource** returns a single element **Stream**)_

```scala
// Replace this:
val driverR: Resource[IO, neotypes.Driver[IO]] = ???

val sessionR = driverR.flatMap(_.session)

val data = fs2.Stream.resource(sessionR).flatMap { s =>
  "MATCH (p:Person) RETURN p.name".query[String].stream[Fs2IoStream](s)
}
```

```scala mdoc:invisible
import cats.effect.{IO, Resource}
import neotypes.cats.effect.implicits._
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._
import neotypes.implicits.syntax.string._
def driverR: Resource[IO, neotypes.Driver[IO]] = ???
```

```scala mdoc:compile-only
// With this:
val data = for {
  driver <- fs2.Stream.resource(driverR)
  session <- driver.streamingSession[Fs2IoStream]
  name <- "MATCH (p:Person) RETURN p.name".query[String].stream(session)
} yield name
```

> For more information, please read [streaming](streams).

### Add auto and semiauto derivation ([#199](https://github.com/neotypes/neotypes/pull/199){:target="_blank"})

Automatic derivation of **Mappers** for case classes is now opt-in,
and **Mappers** for primitive values are now always on the implicit scope
_(without needing any import)_.

Also, we now provide some `semiauto.derive` methods which can be used to cache
**Mapper** instances for case classes _(and product types)_.

This change also gives higher priority to custom **Mappers**
on the companion objects of the target types,
over automatically derived ones.

If you want to keep using automatic derivation you only need to:
```diff
- import import neotypes.implicits.mappers.all._
+ import neotypes.generic.auto._
```

> For more information, please read [supported types](types).

### Support case class by cypher string interpolator ([#201](https://github.com/neotypes/neotypes/pull/201){:target="_blank"})

Now you can pass case class instances directly to cypher string interpolator,
this will add all their properties as parameters of the query.

For example:

```scala mdoc:compile-only
import neotypes.implicits.syntax.cypher._ // Adds the ` interpolator into the scope.

final case class User(name: String, age: Int)
val user = User("my name", 33)
val query = c"""CREATE (u: User { $user }) RETURN u"""
// CREATE (u: User { name: "my name", age: 33 }) RETURN u
```

> For more information, please read [parameterized queries](parameterized_queries).

### Rollback on cancellation ([#???](https://github.com/neotypes/neotypes/pull/???){:target="_blank"})

Cancelling an effectual operation that was using a **Transaction**
will now `rollback` the **Transaction** instead of `commit` it.

## v0.15.1 _(2020-09-08)_

### Fix id handling ([#174](https://github.com/neotypes/neotypes/pull/174){:target="_blank"})

**neotypes** now ensures that if you have a custom `id` property,
that one takes precedence over the system _(**neo4j**)_ one.

> For more information, please read [**neo4j** id](types#neo4j-id).

## v0.15.0 _(2020-07-30)_

### Making Session thread safe ([#163](https://github.com/neotypes/neotypes/pull/163){:target="_blank"})

`neotypes.Session` is now thread safe, by ensuring only one active `neotypes.Transaction` per session.

> For more information, please read [thread safety](driver_session_transaction#session-thread-safety).

## v0.14.0 _(2020-07-10)_

### Upgrade neo4j java driver to version 4 ([#140](https://github.com/neotypes/neotypes/pull/140){:target="_blank"})

**neotypes** is now published for the `v4` version of the **Java** driver,
instead of the `1.7.x` version.

## v0.13.2 _(2020-02-23)_

### Case class mapper for maps ([#60](https://github.com/neotypes/neotypes/pull/60){:target="_blank"})

You can now query for a **case class** when returning a node or a [map projection](https://neo4j.com/docs/cypher-manual/current/syntax/maps/#cypher-map-projection).

### Fix composite types ([#61](https://github.com/neotypes/neotypes/pull/61){:target="_blank"})

**neotypes** now supports all [composite types](https://neo4j.com/docs/cypher-manual/current/syntax/values/#composite-types).

## v0.13.1 _(2020-01-09)_

Bug fixes and improvements to the docs.

> Special thanks to @geoffjohn11

## v0.13.0 _(2019-09-11)_

### Scala 2.13 support ([#45](https://github.com/neotypes/neotypes/pull/45){:target="_blank"})

**neotypes** is now cross compiled for **Scala** `2.12` & `2.13`,
instead of for `2.11` & `2.12`.

## v0.12.0 _(2019-08-25)_
