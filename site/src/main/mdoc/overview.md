---
layout: page
section: overview
title: "Overview"
position: 10
---

# Overview

## Requirements

+ Scala 2.13 / 2.12
+ Java 8+
+ Neo4j 4+

## Session creation

**neotypes** provides the `GraphDatabase` **factory** for creating a **Neo4j** `Driver`, which represents a connection with the Database.
You can use this `Driver`, to create a `Session`, which can be used to perform operations _(`Transactions`)_ over the Database.<br>
This **Scala** classes, are nothing more than simple wrappers over their **Java** counterparts. Which provide a more _"Scala-friendly"_ and functional API.

You can create wrappers for any type `F[_]` for which you have an implementation of the `neotypes.Async` **typeclass** in scope.
The implementation for `scala.concurrent.Future` is built-in in the core module _(for other types, please read [alternative effects](alternative_effects))_.

```scala mdoc:compile-only
import neotypes.GraphDatabase
import org.neo4j.driver.AuthTokens
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.session
```

Please note that, you have to make sure that the driver and session are properly closed at the end of the application execution, to make sure all resources _(such as network connection)_ obtained are cleaned up properly.

You can also use the `readSession` & `writeSession` helpers which will ensure that the session is properly closed after you use it.<br>
Or, if you use other effect types instead of `Future`, for example, `IO`. Then, the creation of both the `Driver` & the `Session` will be wrapped over some kind of **Resource**.

```scala mdoc:compile-only
import neotypes.Driver
import neotypes.generic.auto._ // Allows to automatically derive an implicit ResultMapper for case classes.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final case class Movie(title: String, released: Int)

def result(driver: Driver[Future]): Future[List[Movie]] =
  driver.readSession { session =>
    """MATCH (movie: Movie)
       WHERE lower(movie.title) CONTAINS "thing"
       RETURN movie""".query[Movie].list(session)
  }
```

## Query execution

Once you have a `Session` constructed, you can start querying the database.
The import `neotypes.implicits.syntax.all._` adds an extension method `query[T]` to each string literal in its scope, or you can use the cypher _(`c`)_ string interpolator.

```scala mdoc:invisible
import neotypes.implicits.syntax.all._
def session: neotypes.Session[scala.concurrent.Future] = ???
```

```scala mdoc:compile-only
// Query with plain string.
"CREATE (p: Person { name: 'John', born: 1980 })".query[Unit].execute(session)

import neotypes.types.QueryParam
"CREATE (p: Person { name: $name, born: $born })"
  .query[Unit]
  .withParams(Map("name" -> QueryParam("John"), "born" -> QueryParam(1980)))
  .execute(session)

 // Query with string interpolation.
val name = "John"
val born = 1980
c"CREATE (p: Person { name: $name, born: $born })".query[Unit].execute(session)
```

A query can be run in six different ways:

* `execute(session)` - executes a query that has no return data. Query can be parametrized by `org.neo4j.driver.v1.summary.ResultSummary` or `Unit`.
If you need to support your return types for this type of queries, you can provide an implementation of `ExecutionMapper` for any custom type.
* `single(session)` - runs a query and return a **single** result.
* `list(session)` - runs a query and returns a **List** of results.
* `set(session)` - runs a query and returns a **Set** of results.
* `vector(session)` - runs a query and returns a **Vector** of results.
* `map(session)` - runs a query and returns a **Map** of results _(only if the elements are tuples)_.
* `collectAs(Col)(session)` - runs a query and returns a **Col** of results _(where **Col** is any kind of collection)_. If you are in `2.12` or you are cross-compiling with `2.12` you need to import `neotypes.implicits.mappers.collections._` or you can import `scala.collection.compat._`.
* `stream(session)` - runs a query and returns a **Stream** of results
_(for more information, please read [streaming](streams))_.

```scala mdoc:compile-only
import neotypes.generic.auto._
import neotypes.implicits.mappers.collections._
import org.neo4j.driver.Value
import shapeless.{::, HNil}
import scala.collection.immutable.{ListMap, ListSet}
final case class Movie(title: String, released: Int)
final case class Person(name: String, born: Int)

// Execute.
"CREATE (p:Person { name: 'Charlize Theron', born: 1975 })".query[Unit].execute(session)

// Single.
"MATCH (p:Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(session)
"MATCH (p:Person { name: 'Charlize Theron' }) RETURN p".query[Person].single(session)
"MATCH (p:Person { name: 'Charlize Theron' }) RETURN p".query[Map[String, Value]].single(session)
"MATCH (p:Person { name: '1243' }) RETURN p.born".query[Option[Int]].single(session)

// List.
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].list(session)
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].list(session)

// Set.
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].set(session)
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].set(session)

// Vector.
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].vector(session)
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].vector(session)

// Map.
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].map(session)

// Any collection.
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[Person :: Movie :: HNil].collectAs(ListSet)(session)
"MATCH (p:Person { name: 'Charlize Theron' })-[]->(m:Movie) RETURN p,m".query[(Person, Movie)].collectAs(ListMap)(session)
```
