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
You can use this `Driver` to perform operations _(`Transactions`)_ over the Database.<br>
Those **Scala** classes are nothing more than simple wrappers over their **Java** counterparts, but providing a more _"Scala-friendly"_ and functional API.

You can create wrappers for any effectual type _(`F[_]`)_ for which you have an implementation of the `neotypes.Async` **typeclass** in scope.
The implementation for `scala.concurrent.Future` is built-in in the core module _(for other types, please read [alternative effects](alternative_effects))_.

```scala mdoc:compile-only
import neotypes.GraphDatabase
import neotypes.generic.auto._ // Allows to automatically derive an implicit ResultMapper for case classes.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final case class Movie(title: String, released: Int)

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val result: Future[List[Movie]] =
  """MATCH (movie: Movie)
     WHERE lower(movie.title) CONTAINS "thing"
     RETURN movie""".query[Movie].list(driver)
```

Please remember that, you have to make sure that the `Driver` is properly closed at the end of the application execution, to make sure all obtained resources _(such as network connections)_ are cleaned up properly.<br>
**Note**: for other effect types instead of `Future` _(e.g. `IO`)_, the creation of the `Driver` will be managed by the effect specific implementation of **Resource**; which usually ensures it is properly closed after its use.

## Query execution

Once you have a `Driver` instance, you can start querying the database.
The import `neotypes.implicits.syntax.all._` adds an extension method `query[T]` to each string literal in its scope, or you can use the cypher _(`c`)_ string interpolator.

```scala mdoc:invisible
import neotypes.implicits.syntax.all._
def driver: neotypes.Driver[scala.concurrent.Future] = ???
```

```scala mdoc:compile-only
import neotypes.types.QueryParam

// Simple query.
"CREATE (p: Person { name: 'John', born: 1980 })"
  .query[Unit]
  .execute(driver)

// Query with custom parameters (manual).
"CREATE (p: Person { name: $name, born: $born })"
  .query[Unit]
  .withParams(Map("name" -> QueryParam("John"), "born" -> QueryParam(1980)))
  .execute(driver)

// Query wih custom parameters (string interpolator).
val name = "John"
val born = 1980
c"CREATE (p: Person { name: $name, born: $born })"
  .query[Unit]
  .execute(driver)
```

A query can be run in six different ways:

* `execute(driver)` - executes a query and discards its output, it can be parametrized by `org.neo4j.driver.v1.summary.ResultSummary` or `Unit`
_(if you need to support a different type for this type of queries, you can provide an implementation of `ExecutionMapper`)_.
* `single(driver)` - runs a query and return a **single** result.
* `list(driver)` - runs a query and returns a **List** of results.
* `set(driver)` - runs a query and returns a **Set** of results.
* `vector(driver)` - runs a query and returns a **Vector** of results.
* `map(driver)` - runs a query and returns a **Map** of results
_(only if the elements are tuples)_.
* `collectAs(Col)(driver)` - runs a query and returns a **Col** of results
_(where **Col** is any kind of collection)_.
If you are in `2.12` or you are cross-compiling with `2.12` you need to import `neotypes.implicits.mappers.collections._` or you can import `scala.collection.compat._`.
* `stream(driver)` - runs a query and returns a **Stream** of results
_(for more information, please read [streaming](streams))_.

```scala mdoc:compile-only
import neotypes.generic.auto._
import neotypes.implicits.mappers.collections._
import org.neo4j.driver.Value
import scala.collection.immutable.{ListMap, ListSet}
import shapeless.{::, HNil}

final case class Movie(title: String, released: Int)
final case class Person(name: String, born: Int)

// Execute.
"CREATE (p: Person { name: 'Charlize Theron', born: 1975 })".query[Unit].execute(driver)

// Single.
"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(driver)
"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Person].single(driver)
"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Map[String, Value]].single(driver)
"MATCH (p: Person { name: '1243' }) RETURN p.born".query[Option[Int]].single(driver)

// List.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[Person :: Movie :: HNil].list(driver)
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[(Person, Movie)].list(driver)

// Set.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[Person :: Movie :: HNil].set(driver)
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[(Person, Movie)].set(driver)

// Vector.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[Person :: Movie :: HNil].vector(driver)
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[(Person, Movie)].vector(driver)

// Map.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[(Person, Movie)].map(driver)

// Any collection.
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[Person :: Movie :: HNil].collectAs(ListSet)(driver)
"MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p,m".query[(Person, Movie)].collectAs(ListMap)(driver)
```
