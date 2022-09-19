---
layout: page
section: changelog
title: "Changelog"
position: 100
---

# Changelog

## v0.23.0 _(2022-10-15)_

### Upgrade to neo4j 5 ([#563](https://github.com/neotypes/neotypes/pull/563){:target="_blank"})

We upgraded the **Neo4j** **Java** driver to version `5.0.0`

This version deprecated the internal `RxSession` and
replaced it with a new `ReactiveSession`,
which is based on the `Flow` api provided by the **Java** stdlib since **Java 9**.<br>
Thus, this release drops support for **Java 8**.

## v0.22.0 _(2022-08-03)_

### Separate subproject for generic ([#548](https://github.com/neotypes/neotypes/pull/548){:target="_blank"})

In preparation for supporting **Scala 3**,
we decided to move the `generic` package to its own module.<br>
If you were using the generic derivation
remember to add `neotypes-generic` as a new dependency.

PS: This also means that now the `core` module does not longer depend on **Shapeless**.

> Thanks to @tjarvstrand for tackling this!

### Fix bug using readOnlyTransact in DeferredQuery#execute ([01383667433d29e2119170052b2a9b19eea32999](https://github.com/neotypes/neotypes/commit/01383667433d29e2119170052b2a9b19eea32999){:target="_blank"})

We were using `Driver#readOnlyTransact` in the implementation of
`DeferredQuery#execute(Driver[F], TransactionConfig)` by accident;
which, obviously was wrong and was causing issues.

This new release fixes it and correctly uses `Driver#transact` instead.

> Apologies for the inconveniences this may have caused.

## v0.21.1 _(2022-07-12)_

This release only updates dependencies.<br>
The most notable changes are:

### Update ZIO to 2.0.0 ([#525](https://github.com/neotypes/neotypes/pull/525){:target="_blank"})

The **ZIO** module of **neotypes** now uses the official `2.0.0` stable release.

> Thanks to @masonedmison for initiating this PR _(again)_!

### Update neo4j-java-driver to 4.4.9 ([#539](https://github.com/neotypes/neotypes/pull/539){:target="_blank"})

The latest version of the **Neo4j** **Java** driver
deprecated the `routingDriver` factories; since the connection model changed.
And, as such, we removed the proxies of such factories.

## v0.21.0 _(2022-06-22)_

### Ready only transactions ([#523](https://github.com/neotypes/neotypes/pull/523){:target="_blank"})

**neotypes** now provides some _"read only"_ alternatives
to operations that create and use `Transactions` under the hood;
like `transact` or `query`<br>
The `readOnly` variations automatically override the `withAccessMode`
property of the provided `TransactionConfig` with the `AccessMode.READ` value.

A `readOnly` operation can be routed to a read server,
for more information check the [**Neo4j** docs](https://neo4j.com/docs/api/java-driver/current/org/neo4j/driver/AccessMode.html).

### ZIO 2 support ([#524](https://github.com/neotypes/neotypes/pull/524){:target="_blank"})

The **ZIO** module of **neotypes** not longer supports **ZIO** `1.x`,
but rather requires the a `2.x` release of the library.

> This version uses `2.0.0-RC6`,
> check `0.21.1` _(or upwards)_ for a release that depends on a stable release.

> Thanks to @masonedmison for initiating this PR!

## v0.20.0 _(2022-03-31)_

### Add plain string interpolation ([#493](https://github.com/neotypes/neotypes/pull/493){:target="_blank"})

You can now use `#$` to tell the cypher string interpolator
that the following value should be interpreted as a plain string.

For example:

```scala
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

val name = "John"
val LABEL = "User"

c"CREATE (a: #${LABEL} { name: ${name} })".query[Unit]
// res: neotypes.DeferredQuery[Unit] = DeferredQuery(
//   "CREATE (a: User { name: p1 })",
//   Map("p1" -> neotypes.types.QueryParam("John"))
// )
```

## v0.19.1 _(2022-03-08)_

This was a test release to validate some changes to the release process<br>
It only includes a couple of dependencies bumps.

## v0.19.0 _(2022-03-07)_

### Update to CE3 ([#481](https://github.com/neotypes/neotypes/pull/481){:target="_blank"})

We updated the **cats-effect** and the **fs2** modules to their latest CE3 versions.<br>
The **monix** and **monix-stream** modules still depend on CE2.

## v0.18.3 _(2021-06-25)_

### Reverting the neo4j#797 workaround ([#364](https://github.com/neotypes/neotypes/pull/364){:target="_blank"})

This is just an internal code refactor thanks to a bug fix in the underlying **Java** driver.

However, this refactor implies that users need to update their underlying **Java** driver
to the latest patch versions.<br>
As of the writing date: `4.3.2`, `4.2.7` & `4.1.4`; `4.0` seems to be not longer maintained.

## v0.18.2 _(2021-06-16)_

### Don't override driver's tx-config from DeferredQuery ([#362](https://github.com/neotypes/neotypes/pull/362){:target="_blank"})

This a bug fix that complements #341

It ensures that when you run a query using the standard **DeferredQuery** syntax,
it would respect the current **Driver's** default config.

## v0.18.1 _(2021-06-08)_

### Update organization id ([#359](https://github.com/neotypes/neotypes/pull/359){:target="_blank"})

This is the first version to be released to `io.github.neotypes` rather than to `com.dimafeng`

This releases also changed the name of the **core** module from just `neotypes` to `neotypes-core`

### Ensuring all casting errors during mapping are encapsulated in a NeotypesException ([#345](https://github.com/neotypes/neotypes/pull/345){:target="_blank"})

This is basically a bug fix that ensures that any exception thrown
during the _"casting"_ of **Neo4j** values to **Scala** ones
are catch by the underlying `ValueMapper`
and wrapped in the optional cause of a `neotypes.exceptions.IncoercibleException`

We also removed `neotypes.exceptions.ConversionException`
and replaced its only usage with a `neotypes.exceptions.IncoercibleException`
for consistency.

### Add cypher interpolation of DeferredQueryBuilder ([#344](https://github.com/neotypes/neotypes/pull/344){:target="_blank"})

We added the capability to embedded `DeferredQueryBuilders` inside other queries,
which helps a lot to share and reuse common _(sub)_ queries.

Which means that the following examples now compile out-of-the-box and behave as expected.

```scala
import neotypes.implicits.syntax.cypher._

// Two sub-queries.
val subQuery1Param = 1
val subQuery1 = c"user.id = ${subQuery1Param}"
val subQuery2Param = "Luis"
val subQuery2 = c"user.name = ${subQuery2Param}"
val query1 = c"MATCH (user: User) WHERE ${subQuery1} OR ${subQuery2} RETURN user"

// Sub.query with a sub-query.
val subSubQueryParam = 1
val subSubQuery = c"user.id = ${subSubQueryParam}"
val subQuery = c"""${subSubQuery} OR user.name = "Luis""""
val query2 = c"MATCH (user: User) WHERE ${subQuery} RETURN user"
```

> Special thanks to @tjarvstrand for all the hard work in this PR!

### Make default transaction config configurable ([#341](https://github.com/neotypes/neotypes/pull/341){:target="_blank"})

We added a new `withTransactionConfig` method to the `Driver` interface,
this method can be used to crate a new `Driver[F]` whose default `TransactionConfig`
will be the one passed to the previous method.

This is very useful when you need to use a custom config across all the application, or for tests.

## v0.18.0 _(2021-06-08)_

**DO NOT USE!**

`v0.18.0` was published by accident, use `v0.18.1` which contains all the planned changes for this version.

## v0.17.0 _(2021-04-04)_

### Adding the enumeratum module ([#291](https://github.com/neotypes/neotypes/pull/291){:target="_blank"})

We added a new `neotypes-enumeratum` module which allow the use of
[**Enumeratum**](https://github.com/lloydmeta/enumeratum) enums.

```scala
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

```scala
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

```scala
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
