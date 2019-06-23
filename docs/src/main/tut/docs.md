---
layout: docs
title: "Documentation"
---

# Overview

## Requirements

* Scala 2.12/2.11
* Java 8+
* neo4j 3.*.*+

## Session creation

**neotypes** adds an extension method (`.asScala[F[_]: Async]`) to `org.neo4j.driver.v1.Session` and `org.neo4j.driver.v1.Driver` that allows to build a `neotypes`'s session and driver wrappers.
You can parametrize `asScala` by any type that you have a typeclass `neotypes.Async` implementation for.
The typeclass implementation for `scala.concurrent.Future` is built-in (Please read more about [side effect implementations](docs/alternative_effects.html).
Please note that you have to make sure that the session is properly closed at the end of the application execution if you decide to manage session lifecycle manually.

```scala
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.session().asScala[Future]
```

Please note that the session should be closed to make sure all resources (such as network connection) obtained by the session will always be cleaned up properly.

```scala
session.close()
```

Or you may decide to use `neotypes.Driver` to help you with it.

```scala
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "****")).asScala[Future]

val result: Future[Seq[Movie]] = driver.readSession { session =>
    c"""MATCH (movie:Movie)
        WHERE lower(movie.title) CONTAINS ${query.toLowerCase}
        RETURN movie""".query[Movie].list(session)
  }
```

In this case, the session will be properly closed after query execution.

## Query execution

Once you have a session constructed, you can start querying the database.
The import `neotypes.implicits.all._` adds an extension method `query[T]` to each string literal in its scope, or you can use string interpolation.
Type parameter `[T]` specifies a resulted return type.

```scala
"create (p:Person {name: $name, born: $born})".query[Unit].execute(s)
"create (p:Person {name: $name, born: $born})".query[Unit].withParams(Map("name" -> "John", "born" -> 1980)).execute(s)

val name = "John"
val born = 1980
c"create (p:Person {name: $name, born: $born})".query[Unit].execute(s) // Query with string interpolation.
```

A query can be run in three different ways:

* `execute(s)` - executes a query that has no return data. Query can be parametrized by `org.neo4j.driver.v1.summary.ResultSummary` or `Unit`.
If you need to support your return types for this type of queries, you can provide an implementation of `ExecutionMapper` for any custom type.
* `single(s)` - runs a query and return a single result.
* `list(s)` - runs a query and returns a list of results.
* `set(s)` - runs a query and returns a set of results.

```scala
// Execute.
"create (p:Person {name: $name, born: $born})".query[Unit].execute(s)

// Single.
"match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
"match (p:Person {name: 'Charlize Theron'}) return p".query[Person].single(s)
"match (p:Person {name: 'Charlize Theron'}) return p".query[Map[String, Value]].single(s)
"match (p:Person {name: '1243'}) return p.born".query[Option[Int]].single(s)

// List.
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].list(s)

// Set.
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].set(s)
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].set(s)
```
