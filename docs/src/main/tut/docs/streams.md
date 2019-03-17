---
layout: docs
title: "Streams"
---

# Streams

Starting version `0.5.0`, you can stream the query result. As of `0.5.0`, there is only one implementation of streaming - Akka Stream.

## Usage

```scala
import neotypes.Async._
import neotypes.akkastreams.AkkaStream._
import neotypes.implicits._

val session = driver.session().asScala[Future]
implicit val system = ActorSystem("QuickStart")
implicit val materializer = ActorMaterializer()

"match (p:Person) return p.name"
      .query[String]
      .stream[AkkaStream.Stream, Future](session)
      .runWith(Sink.foreach(println))
``` 

The code snippet above is lazily retrieving data from neo4j loading each element of the result only when it's requested.
This approach aims to improve performance and memory footpring with large volumes of resulted data.

## Transaction management

You have two options in transaction management:
* **Manual**: if you use `neotypes.Transaction` and call `stream`, you need to ensure that the transaction is gracefully closed after reading is finished.
* **Auto-closing**: when you work with `neotypes.Session`, you may wish to automatically commit (or rollback on failure) the transaction once
all elements are consumed.

## Alternative stream implementations

If you don't see your stream supported, you can add your implementation of `neotypes.Stream[S[_], F[_]]` typeclass and include to the implicit scope.
The type parameters in the signature indicate:
* `S[_]` - type of your stream
* `F[_]` - effect that will be used to produce each element retrieval