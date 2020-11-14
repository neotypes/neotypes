---
layout: page
section: changelog
title: "Changelog"
position: 100
---

# Changelog

## 0.16.0 _(2020-11-??)_

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

## 0.15.1 _(2020-09-08)_

### Fix id handling ([#174](https://github.com/neotypes/neotypes/pull/174){:target="_blank"})

**neotypes** now ensures that if you have a custom `id` property,
that one takes precedence over the system _(**neo4j**)_ one.

> For more information, please read [**neo4j** id](types#neo4j-id).
## 0.15.0 _(2020-07-30)_
