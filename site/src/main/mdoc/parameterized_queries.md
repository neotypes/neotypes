---
layout: page
section: queries
title: "Parameterized Queries"
position: 20
---

# Parameterized Queries

**neotypes** leverages [StringContext](https://docs.scala-lang.org/overviews/core/string-interpolation.html) for interpolating query parameters.
And uses the `ParameterMapper` typeclass to ensure typesafety.

```scala mdoc:silent
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.
```

Which can be used like this:

```scala mdoc:nest
val name = "John"
val query = c"CREATE (a: Test { name: $name })".query[Unit]

assert(query.query == "CREATE (a: Test { name: $p1 })")
assert(query.params == Map("p1" -> neotypes.types.QueryParam("John")))
```

All parameters will be converted to neo4j supported types _(please see [Supported types](types))_.

The `c` interpolator creates a `DeferredQueryBuilder` which is an immutable representation of a cypher query.
You can concatenate `DeferredQueryBuilder`s with other `DeferredQueryBuilder`s or `String`s, to build complex queries.

```scala mdoc:nest
val name = "John"
val born = 1980
val query1 = c"CREATE (a: Test { name: $name, " + c"born: $born })"
query1.query[Unit]

val LABEL = "User"
val query2 = c"CREATE (a: " + LABEL + c"{ name: $name })"
query2.query[Unit]
```

A case class can be used directly in the interpolation:

```scala mdoc:nest
import neotypes.generic.auto._ // Provides automatic derivation of ParameterMapper for any case class.

final case class User(name: String, born: Int)
final case class Cat(tag: String)
final case class HasCat(since: Int)

val user = User("John", 1980)
val cat = Cat("Waffles")
val hasCat = HasCat(2010)

val query1 = c"CREATE (u: User { $user })"
query1.query[Unit]

val query2 = c"CREATE (u: User { $user })-[r: HAS_CAT { $hasCat }]->(c: Cat { $cat }) RETURN r"
query2.query[HasCat]
```
