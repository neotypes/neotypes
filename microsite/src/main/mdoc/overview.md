---
layout: page
section: overview
title: "Overview"
position: 10
---

# Overview

## Requirements

+ **Scala** `2.12` / `2.13`
+ **Java** `17+`
+ **Neo4j** server `4+`
+ **Neo4j** driver `5+`

## Driver creation

**neotypes** provides the `GraphDatabase` factory for creating a **Neo4j** `Driver`,
which represents a connection with the Database.
You can use this `Driver` to perform operations _(`Transactions`)_ over the Database.<br>
Those **Scala** classes are nothing more than simple wrappers over their **Java** counterparts,
but providing a more _"Scala-friendly"_ and functional API.

You can create wrappers for any effect type _(`F[_]`)_
for which you have an implementation of the `neotypes.Async` typeclass in scope.
The implementation for `scala.concurrent.Future` is built-in
in the `core` module _(for other types, please read [alternative effects](alternative_effects))_.

```scala mdoc:compile-only
import neotypes.GraphDatabase
import neotypes.generic.implicits._ // Allows to automatically derive an implicit ResultMapper for case classes.
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.syntax.all._ // Provides the query extension method.
import org.neo4j.driver.AuthTokens
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final case class Movie(title: String, released: Int)

val driver =
  GraphDatabase.asyncDriver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val result: Future[List[Movie]] =
  """MATCH (movie: Movie)
     WHERE lower(movie.title) CONTAINS "thing"
     RETURN movie
  """.query(ResultMapper.productDerive[Movie]).list(driver)
```

Please remember that, you have to make sure that the `Driver` is properly closed
at the end of the application execution,
to make sure all obtained resources _(such as network connections)_ are cleaned up properly.<br>
**Note**: for other effect types instead of `Future` _(e.g. `IO`)_,
the creation of the `Driver` will be managed by the effect specific implementation of `Resource`;
which usually ensures it is properly closed after its use.

## Query execution

Once you have a `Driver` instance, you can start querying the database.
The import `neotypes.syntax.all._` adds an extension method `query`
to each string literal in its scope,
or you can use the **Cypher** _(`c`)_ string interpolator.

```scala mdoc:invisible
import neotypes.syntax.all._
def driver: neotypes.AsyncDriver[scala.concurrent.Future] = ???
```

```scala mdoc:compile-only
import neotypes.model.query.QueryParam

// Simple query.
"CREATE (p: Person { name: 'John', born: 1980 })"
  .execute
  .void(driver)

// Query with custom parameters (manual).
"CREATE (p: Person { name: $name, born: $born })"
  .execute
  .withParams(Map("name" -> QueryParam("John"), "born" -> QueryParam(1980)))
  .void(driver)

// Query wih custom parameters (string interpolator).
val name = "John"
val born = 1980
c"CREATE (p: Person { name: ${name}, born: ${born} })"
  .execute
  .void(driver)
```

A query can be run in different ways:

+ `execute.void(driver)` - Executes a query and discards its output.
+ `execute.resultSummary(driver)` - Executes a query and returns only its `ResultSummary`.
+ `query(mapper).single(driver)` - Runs a query and returns a single result.
+ `query(mapper).list(driver)` - Runs a query and returns a `List` of results.
+ `query(mapper).set(driver)` - Runs a query and returns a `Set` of results.
+ `query(mapper).vector(driver)` - Runs a query and returns a `Vector` of results.
+ `query(mapper).map(driver)` - Runs a query and returns a `Map` of results
_(only if the elements are tuples)_.
+ `query(mapper).collectAs(Col, driver)` - Runs a query and returns the supplied type of collection of results.
+ `query(mapper).stream(driver)` - Runs a query and returns a `Stream `of results
_(for more information, please read [streaming](streams))_.
+ `query(mapper).withResultSummary.single(driver)` - Runs a query and returns a single result plus its `ResultMapper`
_(you may call any of the other query options after `withResultSummary`)_.

```scala mdoc:compile-only
import neotypes.generic.implicits._ // Allows to automatically derive an implicit ResultMapper for case classes.
import neotypes.mappers.ResultMapper // Allows to decode query results.
import scala.collection.immutable.ListSet

final case class Movie(title: String, released: Int)
final case class Person(name: String, born: Int)

// Execute.
"CREATE (p: Person { name: 'Charlize Theron', born: 1975 })"
  .execute
  .void(driver)

// Single.
"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
  .query(ResultMapper.string)
  .single(driver)
"MATCH (p: Person { name: '1243' }) RETURN p.born"
  .query(ResultMapper.option(ResultMapper.int))
  .single(driver)
"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p"
  .query(ResultMapper.productDerive[Person])
  .single(driver)

// List.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m"
  .query(ResultMapper.tuple[Person, Movie])
  .list(driver)

// Set.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m"
  .query(ResultMapper.tuple[Person, Movie])
  .set(driver)

// Vector.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m"
  .query(ResultMapper.tuple[Person, Movie])
  .vector(driver)

// Map.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m"
  .query(ResultMapper.tuple[Person, Movie])
  .map(driver)

// Any collection.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m"
  .query(ResultMapper.tuple[Person, Movie])
  .collectAs(ListSet, driver)
```
