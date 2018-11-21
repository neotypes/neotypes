![Logo](neotypes.png)

> neotype - a type specimen that is selected subsequent to the description of a species to replace a preexisting type that has been lost or destroyed

[![Build Status](https://travis-ci.org/neotypes/neotypes.svg?branch=master)](https://travis-ci.org/neotypes/neotypes)

# neotypes

**Scala type-safe asynchronous driver (not opinionated on side-effect implementation) for neo4j**.

* **Scala** - the driver provides you with support for all standard Scala types without the need to convert Scala <-> Java types back and forth and you can easily add your types.
* **Type-safe** - the driver leverages [typeclasses](https://blog.scalac.io/2017/04/19/typeclasses-in-scala.html) to derive all needed conversions at the compile time.
* **Asynchronous** - the driver sits on top of [asynchronous Java driver](https://neo4j.com/blog/beta-release-java-driver-async-api-neo4j/).
* **Not opinionated on side-effect implementation** - you can use it with any implementation of side-effects of your chose (scala.Future, cats-effect
 IO, Monix Task, etc) by implementing a simple typeclass. `scala.Future` is implemented and comes out of the box.

The project aims to provide seamless integration with most popular scala infrastructures such as lightbend (Akka, Akka-http, Lagom, etc), typelevel (cats, http4s, etc), twitter (finch, etc)...


## Requirements

* Scala 2.12
* Java 8+
* neo4j 3.4.*+

## Usage

```scala
    import neotypes.Async._
    import neotypes.implicits._
    import shapeless._

  case class Person(id: Long, born: Int, name: Option[String], f: Option[Int])

  case class Movie(id: Long, released: Int, title: String)

    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String]().single(s)
      int <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Int]().single(s)
      long <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Long]().single(s)
      double <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Double]().single(s)
      float <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Float]().single(s)
      cc <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Person]().single(s)
      hlist <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil]().list(s)
    } yield {
      assert(string == "Charlize Theron")
      assert(int == 1975)
      assert(long == 1975)
      assert(Math.abs(double - 1975) < 0.0001)
      assert(Math.abs(float - 1975) < 0.0001)
      assert(cc.id >= 0)
      assert(cc.name.contains("Charlize Theron"))
      assert(cc.born == 1975)
      assert(cc.f.isEmpty)
      assert(hlist.size == 1)
      assert(hlist.head.head.name.contains("Charlize Theron"))
      assert(hlist.head.last.title == "That Thing You Do")
    }
```

## Supported types


| Type                                      | Query result   | Field of a case class | Query parameter  |
| ----------------------------------------- |:--------------:| :--------------------:|:-----------------|
| `scala.Int                             `  | ✓              |✓||
| `scala.Long                            `  | ✓              |✓||
| `scala.Double                          `  | ✓              |✓||
| `scala.Float                           `  | ✓              |✓||
| `java.lang.String                      `  | ✓              |✓||
| `scala.Option[T]                       `  | ✓              |✓||
| `scala.Boolean                         `  | ✓              |✓||
| `scala.Array[Byte]                     `  | ✓              |✓||
| `java.time.LocalDate                   `  | ✓              |✓||
| `java.time.LocalTime                   `  | ✓              |✓||
| `java.time.LocalDateTime               `  | ✓              |✓||
| `org.neo4j.driver.v1.Value             `  | ✓              |||
| `org.neo4j.driver.v1.types.Node        `  | ✓              |✓||
| `org.neo4j.driver.v1.types.Relationship`  | ✓              |✓||
| `shapeless.HList                       `  | ✓              |||
| `neotypes.types.Path                   `  | ✓              |||
| `User defined case class               `  | ✓              |||


## Side-effect implementation

In order to support your implementation of side-effects, you need to implement `neotypes.Async[YourIO]` and add to the implicit scope.

## Roadmap

TODO

## Release notes

TODO

## Publishing

TODO
