---
layout: docs
title: "Documentation"
---

# Overview

## Requirements

* Scala 2.12/2.11
* Java 8+
* neo4j 3+

## Session creation

**neotypes** provides the `GraphDatabase` **factory** for **Neo4j** `Session` and `Driver` wrappers.
You can create wrappers for any type `F[_]` for which you have an implementation of the `neotypes.Async` **typeclass**.
The implementation for `scala.concurrent.Future` is built-in _(for other types, please read [side effects](docs/alternative_effects.html))_.

Please note that you have to make sure that the driver and session is properly closed at the end of the application execution.

```scala
import neotypes.GraphDatabase

val program: F[Unit] = for {
  driver <- GraphDatabase.driver[F]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session()
  ...
  _ <- session.close()
  _ <- driver.close()
yield ()
```

Please note that the session should be closed to make sure all resources _(such as network connection)_ obtained by the session will always be cleaned up properly.

Or you may decide to use `neotypes.Driver` to help you with it.

```scala
import neotypes.GraphDatabase

val driverF: Future[Driver[Future]] =
  GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val result: Future[List[Movie]] = driverF.flatMap { driver =>
  driver.readSession { session =>
    """MATCH (movie:Movie)
        WHERE lower(movie.title) CONTAINS "Thing"
        RETURN movie""".query[Movie].list(session)
  }
}
```

In this case, the session will be properly closed after query execution.

## Query execution

Once you have a session constructed, you can start querying the database.
The import `neotypes.implicits.all._` adds an extension method `query[T]` to each string literal in its scope, or you can use string interpolation.
Type parameter `[T]` specifies the result type.

```scala
"create (p:Person {name: $name, born: $born})".query[Unit].execute(s)
"create (p:Person {name: $name, born: $born})".query[Unit].withParams(Map("name" -> "John", "born" -> 1980)).execute(s)

val name = "John"
val born = 1980
c"create (p:Person {name: $name, born: $born})".query[Unit].execute(s) // Query with string interpolation.
```

A query can be run in six different ways:

* `execute(s)` - executes a query that has no return data. Query can be parametrized by `org.neo4j.driver.v1.summary.ResultSummary` or `Unit`.
If you need to support your return types for this type of queries, you can provide an implementation of `ExecutionMapper` for any custom type.
* `single(s)` - runs a query and return a **single** result.
* `list(s)` - runs a query and returns a **List** of results.
* `set(s)` - runs a query and returns a **Set** of results.
* `vector(s)` - runs a query and returns a **Vector** of results.
* `map(s)` - runs a query and returns a **Map** of results _(only if the elements are tuples)_.
* `stream(s)` - runs a query and returns a **Stream** of results
_(please read more [streaming](docs/streams.html))_.

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

// Vector.
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].vector(s)
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].vector(s)

// Map.
"match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].map(s)
```
