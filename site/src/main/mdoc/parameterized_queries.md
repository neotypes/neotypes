---
layout: page
section: queries
title: "Parameterized Queries"
position: 20
---

# Parameterized Queries

**neotypes** leverages [StringContext](https://docs.scala-lang.org/overviews/core/string-interpolation.html) for interpolating query parameters.
And uses the `ParameterMapper` typeclass to ensure typesafety.

Which can be used like this:

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

val name = "John"
c"CREATE (a: Test { name: ${name} })".query[Unit]
```

All parameters will be converted to neo4j supported types _(please see [Supported types](types))_.

The `c` interpolator creates a `DeferredQueryBuilder` which is an immutable representation of a cypher query.
You can concatenate `DeferredQueryBuilder`s with other `DeferredQueryBuilder`s or `String`s, to build complex queries.

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

val name = "John"
val born = 1980
val query1 =
  c"CREATE (a: Test { name: ${name}, " +
  c"born: ${born} })"
query1.query[Unit]

val LABEL = "User"
val query2 = c"CREATE (a: " + LABEL + c"{ name: $name })"
query2.query[Unit]
```

Alternatively, you can also have plain interpolation by using `#$` instead of `$`

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

val name = "John"
val LABEL = "User"

c"CREATE (a: #${LABEL} { name: ${name} })".query[Unit]
```

You may also use triple quotes to split the query in multiple lines:

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

val name = "John"
val born = 1980
val LABEL = "User"

c"""CREATE (a: #${LABEL} {
      name: ${name},
      born: ${born}
    })""".query[Unit]
```

A case class can be used directly in the interpolation:

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.
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

A `DeferredQuery` can also be interpolated inside another one:

```scala mdoc:reset
import neotypes.implicits.syntax.cypher._ // Adds the `c` interpolator into the scope.

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
```
