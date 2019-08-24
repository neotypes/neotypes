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

**neotypes** provides the `GraphDatabase` **factory** for creating a **Neo4j** `Driver`, which represents a connection with the Database.
You can use this `Driver`, to create a `Session`, which can be used to perform operations _(`Transactions`)_ over the Database.<br>
This **Scala** classes, are nothing more than simple wrappers over their **Java** counterparts. Which provide a more _"Scala-friendly"_ and functional API.

You can create wrappers for any type `F[_]` for which you have an implementation of the `neotypes.Async` **typeclass** in scope.
The implementation for `scala.concurrent.Future` is built-in in the core module _(for other types, please read [alternative effects](docs/alternative_effects.html))_.

```scala
import neotypes.GraphDatabase
import scala.concurrent.Future
import scala.concurrent.ExecutionContex.Implicits.global

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.session
```

Please note that, you have to make sure that the driver and session are properly closed at the end of the application execution, to make sure all resources _(such as network connection)_ obtained are cleaned up properly.

You can also use the `readSession` & `writeSession` helpers which will ensure that the session is properly closed after you use it.<br>
Or, if you use other effect type instead of `Future`, for example `IO`. Then, the creation of both the `Driver` & the `Session` will be wrapped over some kind of **Resource**.

```scala
import neotypes.GraphDatabase

val driver: Driver[Future] = ???

val result: Future[List[Movie]] =
  driver.readSession { session =>
    """MATCH (movie:Movie)
        WHERE lower(movie.title) CONTAINS "Thing"
        RETURN movie""".query[Movie].list(session)
  }
```

## Query execution

Once you have a session constructed, you can start querying the database.
The import `neotypes.implicits.all._` adds an extension method `query[T]` to each string literal in its scope, or you can use string interpolation.
Type parameter `[T]` specifies the result type.

```scala
"CREATE (p:Person {name: $name, born: $born})".query[Unit].execute(s)
"CREATE (p:Person {name: $name, born: $born})".query[Unit].withParams(Map("name" -> "John", "born" -> 1980)).execute(s)

val name = "John"
val born = 1980
c"CREATE (p:Person {name: $name, born: $born})".query[Unit].execute(s) // Query with string interpolation.
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
_(for more information, please read [streaming](docs/streams.html))_.

```scala
// Execute.
"CREATE (p:Person {name: $name, born: $born})".query[Unit].execute(s)

// Single.
"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p".query[Person].single(s)
"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p".query[Map[String, Value]].single(s)
"MATCH (p:Person {name: '1243'}) RETURN p.born".query[Option[Int]].single(s)

// List.
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].list(s)
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].list(s)

// Set.
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].set(s)
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].set(s)

// Vector.
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].vector(s)
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].vector(s)

// Map.
"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].map(s)
```
