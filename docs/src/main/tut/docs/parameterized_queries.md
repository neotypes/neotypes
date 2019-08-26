---
layout: docs
title: "Parameterized Queries"
---

# Parameterized Queries

**neotypes** leverages [StringContext](https://docs.scala-lang.org/overviews/core/string-interpolation.html) for interpolating query parameters.
`import neotypes.implicits.syntax.cypher._` adds the `c` interpolator into the implicit scope.

```scala
val name = "John"
val query = c"CREATE (a:Test {name: $name})"

assert(query.rawQuery == "CREATE (a:Test {name: $p1})")
assert(query.params == Map("p1" -> QueryParam("John")))
```

All parameters will be converted to neo4j supported types (please see [Supported types](types.html)).

The `c` interpolator creates a `DeferredQueryBuilder` which is an immutable representation of a cypher query.
You can concatenate `DeferredQueryBuilder`s with other `DeferredQueryBuilder`s or `String`s, to build complex queries.

```scala
val name = "John"
val born = 1980
val query = c"CREATE (a:Test {name: $name," + c"born: $born})"

val LABEL = "User"
val query2 = c"CREATE (a:" + LABEL + c"{name: $name})"
```
