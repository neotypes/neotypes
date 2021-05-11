---
layout: page
section: changelog
title: "Changelog"
position: 100
---

# Changelog

## v0.17.0 _(2021-04-04)_

### Adding the enumeratum module ([#291](https://github.com/neotypes/neotypes/pull/291){:target="_blank"})

We added a new `neotypes-enumeratum` module which allow the use of
[**Enumeratum**](https://github.com/lloydmeta/enumeratum) enums.

```scala mdoc:compile-only
import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{StringEnum, StringEnumEntry}
import neotypes.enumeratum.{NeotypesEnum, NeotypesKeyEnum, NeotypesStringEnum}

sealed trait SimpleEnum extends EnumEntry with Product with Serializable
object SimpleEnum extends Enum[SimpleEnum] with NeotypesEnum[SimpleEnum] {
  final case object Foo extends SimpleEnum
  final case object Bar extends SimpleEnum
  final case object Baz extends SimpleEnum

  val values = findValues
}
implicitly[neotypes.mappers.ResultMapper[SimpleEnum]]
implicitly[neotypes.mappers.ParameterMapper[SimpleEnum]]

sealed trait KeyEnum extends EnumEntry with Product with Serializable
object KeyEnum extends Enum[KeyEnum] with NeotypesKeyEnum[KeyEnum] {
  final case object Key1 extends KeyEnum
  final case object Key2 extends KeyEnum
  final case object Key3 extends KeyEnum

  val values = findValues
}
implicitly[neotypes.mappers.ParameterMapper[Map[KeyEnum, Int]]]

sealed abstract class KeyStringEnum (val value: String) extends StringEnumEntry with Product with Serializable
object KeyStringEnum extends StringEnum[KeyStringEnum] with NeotypesStringEnum[KeyStringEnum] {
  final case object KeyA extends KeyStringEnum(value = "keyA")
  final case object KeyB extends KeyStringEnum(value = "keyB")
  final case object KeyC extends KeyStringEnum(value = "keyC")

  val values = findValues
}
implicitly[neotypes.mappers.ParameterMapper[Map[KeyStringEnum, Int]]]
implicitly[neotypes.mappers.ValueMapper[KeyStringEnum]]
```

> For more information, please read [supported types](types).

### Adding UnwrappedMappers semiauto derivation ([#294](https://github.com/neotypes/neotypes/pull/294){:target="_blank"})

We added a new semiauto derivation of mappers for
`AnyVal` case classes that act like the underlying type.

```scala mdoc:reset-object
import neotypes.generic.semiauto
import neotypes.mappers.{ParameterMapper, ValueMapper}

final case class Id(value: String) extends AnyVal
implicit final val idParameterMapper: ParameterMapper[Id] = semiauto.deriveUnwrappedParameterMapper
implicit final val idValueMapper: ValueMapper[Id] = semiauto.deriveUnwrappedValueMapper
```

> For more information, please read [supported types](types).

### Adding version scheme ([#281](https://github.com/neotypes/neotypes/pull/281){:target="_blank"})

Since this version, **neotypes** artifacts include its version scheme
which can be read by **sbt** to produce more accurate eviction errors.

> For more information, please read [this blog post](https://scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html).

## v0.16.0 _(2021-02-09)_

### Removing Session & Using the new Rx module for Streaming ([#221](https://github.com/neotypes/neotypes/pull/221){:target="_blank"})

We replaced our in-house implementation of **Streaming**
with wrappers for the new `Rx` module of the **Java** driver.

During this change we also removed `Session` since it wasn't actually needed.

This change implies that now you can chose between a
normal `Driver[F]` or a `StreamingDriver[S, F]`,
the latter can be used to create both a
normal `Transaction[F]` or a `StreamingTransaction[S, F]`.
The first one no longer supports streaming data from the database,
but the second one implies that even no-streaming operations like `single`
are implemented in terms of `ReactiveStreams`.<br>
However, if you use single query + auto-commit syntax
provided by `DeferredQuery` then you will use normal _(asynchronous)_
**Transactions** for most operations and **Streaming**
**Transactions** only for `stream` queries.

This is quite a big change, because now the `Stream` typeclass
doesn't need to be in scope when calling `stream`,
but rather when creating the **Driver**;
By calling `GraphDatabase.streamingDriver[S[_]]`

```scala
// Replace this:
val driverR: Resource[IO, neotypes.Driver[IO]] = ???

val sessionR = driverR.flatMap(_.session)

val data = fs2.Stream.resource(sessionR).flatMap { s =>
  "MATCH (p:Person) RETURN p.name".query[String].stream[Fs2IoStream](s)
}
```

```scala
import cats.effect.{IO, Resource}
import neotypes.{GraphDatabase, StreamingDriver}
import neotypes.cats.effect.implicits._
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.AuthTokens
implicit val cs =IO.contextShift(scala.concurrent.ExecutionContext.global)
```

```scala
// With this:
val driverR: Resource[IO, StreamingDriver[Fs2IoStream, IO]] =
  GraphDatabase.streamingDriver[Fs2IoStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val data = for {
  driver <- fs2.Stream.resource(driverR)
  name <- "MATCH (p:Person) RETURN p.name".query[String].stream(driver)
} yield name
```

> For more information, please read [streaming](streams).

### Rollback on cancellation ([c6c43d24f8e0c82db40387fa600490c4b85fa297](https://github.com/neotypes/neotypes/commit/c6c43d24f8e0c82db40387fa600490c4b85fa297){:target="_blank"})

Cancelling an effectual operation that was using a **Transaction**
will now `rollback` the **Transaction** instead of `commit` it.

> **Note**: This should have been its own PR but due the problems on [#164](https://github.com/neotypes/neotypes/pull/164){:target="_blank"} it ended up being a commit on [#221](https://github.com/neotypes/neotypes/pull/221){:target="_blank"}.

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
import neotypes.generic.auto._ // Provides automatic derivation of ParameterMapper for any case class.
import neotypes.implicits.syntax.cypher._ // Adds the ` interpolator into the scope.

final case class User(name: String, age: Int)
val user = User("my name", 33)
val query = c"""CREATE (u: User { $user }) RETURN u"""
// CREATE (u: User { name: "my name", age: 33 }) RETURN u
```

> For more information, please read [parameterized queries](parameterized_queries).

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
