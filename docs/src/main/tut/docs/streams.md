---
layout: docs
title: "Streams"
---

# Streams

**Neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream. 
Currently, there are two implementations of streaming supported out of the box - [Akka Streams](https://doc.akka.io/docs/akka/current/stream/index.html)  [FS2](https://fs2.io/).

## Usage

### Akka Streams

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

### FS2 _(with cats.effect.IO)_

```scala
import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.fs2.implicits._
import neotypes.implicits._

type Fs2Stream[T] = fs2.Stream[IO, T]

val s = driver.session().asScala[IO]

"match (p:Person) return p.name"
  .query[Int]
  .stream[Fs2Stream, IO](s)
  .evalMap(n => IO(println(n)))
  .compile
  .drain
  .unsafeRunSync()
```

The code snippets above are lazily retrieving data from neo4j, loading each element of the result only when it's requested and rolls back the transaction once all elements are read.
This approach aims to improve performance and memory footprint with large volumes of returning data.


## Transaction management

You have two options in transaction management:
* **Manual**: if you use `neotypes.Transaction` and call `stream`, you need to ensure that the transaction is gracefully closed after reading is finished.
* **Auto-closing**: you may wish to automatically rollback the transaction once
all elements are consumed. This behaviour is provided by `DeferredQuery.stream(Session[F])`

## Alternative stream implementations

If you don't see your stream supported, you can add your implementation of `neotypes.Stream[S[_], F[_]]` typeclass and include to the implicit scope.
The type parameters in the signature indicate:
* `S[_]` - a type of your stream.
* `F[_]` - an effect that will be used to produce each element retrieval.
