---
layout: page
section: queries
title: "Parameterized Queries"
position: 20
---

# Parameterized Queries

**neotypes** leverages [StringContext](https://docs.scala-lang.org/overviews/core/string-interpolation.html) for interpolating query parameters.
And uses the `ParameterMapper` typeclass to ensure type-safety.

Which can be used like this:

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.

val name = "John"

val query = c"CREATE (a: Test { name: ${name} })"
query.execute
```

All parameters will be converted to**Neo4j** supported types _(please see [Supported types](types))_.<br>
The `c` interpolator creates a `DeferredQueryBuilder` which is an immutable representation of a **Cyypher** query.
You can concatenate `DeferredQueryBuilder`s with other `DeferredQueryBuilder`s or `String`s, to build complex queries.

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.

val name = "John"
val born = 1980
val LABEL = "User"

val query =
  c"CREATE (a: " +
  LABEL +
  c"{ name: $name }, " +
  c"born: ${born} })"
query.execute
```

Alternatively, you can also have plain interpolation by using `#$` instead of `$`

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.

val name = "John"
val LABEL = "User"

val query = c"CREATE (a: #${LABEL} { name: ${name} })"
query.execute
```

You may also use triple quotes to split the query in multiple lines:

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.

val name = "John"
val born = 1980
val LABEL = "User"

val query =
  c"""CREATE (a: #${LABEL} {
      name: ${name},
      born: ${born}
    })
   """
query.execute
```

A case class can be used directly in the interpolation:

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.
import neotypes.generic.implicits._ // Provides automatic derivation of ParameterMapper for any case class.

final case class User(name: String, born: Int)
final case class Cat(tag: String)
final case class HasCat(since: Int)

val user = User("John", 1980)
val cat = Cat("Waffles")
val hasCat = HasCat(2010)

val query = c"CREATE (u: User { $user })-[r: HAS_CAT { $hasCat }]->(c: Cat { $cat })"
query.execute
```

A `DeferredQueryBuilder` can also be interpolated inside another one:

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.

// Two sub-queries.
val subQuery1Param = 1
val subQuery1 = c"user.id = ${subQuery1Param}"
val subQuery2Param = "Luis"
val subQuery2 = c"user.name = ${subQuery2Param}"

val query = c"MATCH (user: User) WHERE ${subQuery1} OR ${subQuery2} RETURN user"
query.execute
```

You may also interpolate heterogeneous `Lists` and `Maps` _(of supported types)_:

```scala mdoc:reset
import neotypes.syntax.all._ // Adds the `c` interpolator into the scope.
import neotypes.model.query.QueryParam

val hlist = List(
  QueryParam(1),
  QueryParam("Luis"),
  QueryParam(true)
)

val hmap = Map(
  "foo" -> QueryParam(3),
  "bar" -> QueryParam("Balmung"),
  "baz" -> QueryParam(false)
)

// Unwind the list to create many nodes.
val query1 = c"UNWIND ${hlist} AS x CREATE (: Node { data: x } )"
query1.execute

// Use the map as the properties of a Node.
val query2 = c"CREATE (: Node ${hmap} )"
query2.execute
```
