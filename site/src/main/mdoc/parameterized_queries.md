---
layout: page
section: queries
title: "Parameterized Queries"
position: 20
---

# Parameterized Queries

**neotypes** leverages [StringContext](https://docs.scala-lang.org/overviews/core/string-interpolation.html) for interpolating query parameters.
And uses the `ParameteterMapper` typeclass to ensure typesafety.

```scala mdoc:silent
import neotypes.implicits.mappers.parameters._ // Brings all the default ParameterMappers into the scope.
import neotypes.implicits.syntax.cypher._ // Adds the ` interpolator into the scope.
```

Which can be used like this:

```scala mdoc:nest
val name = "John"
val query = c"CREATE (a:Test {name: $name})".query[Unit]

assert(query.query == "CREATE (a:Test {name: $p1})")
assert(query.params == Map("p1" -> neotypes.types.QueryParam("John")))
```

All parameters will be converted to neo4j supported types (please see [Supported types](types.html)).

The `c` interpolator creates a `DeferredQueryBuilder` which is an immutable representation of a cypher query.
You can concatenate `DeferredQueryBuilder`s with other `DeferredQueryBuilder`s or `String`s, to build complex queries.

```scala mdoc:nest
val name = "John"
val born = 1980
val query1 = c"CREATE (a:Test {name: $name," + c"born: $born})"
query1.query[Unit]

val LABEL = "User"
val query2 = c"CREATE (a:" + LABEL + c"{name: $name})"
query2.query[Unit]
```
