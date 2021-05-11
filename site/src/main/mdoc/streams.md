---
layout: page
section: streams
title: "Streaming"
position: 50
---

# Streaming

**neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream.
Currently, there are four supported implementations _(one for each effect type)_.
[**Akka Streams**](https://doc.akka.io/docs/akka/current/stream/index.html) _(for `scala.concurrent.Future`)_,
[**FS2**](https://fs2.io/) _(for `cats.effect.Async[F]`)_,
[**Monix Observables**](https://monix.io/docs/3x/reactive/observable.html) _(for `monix.eval.Task`)_ &
[**ZIO ZStreams**](https://zio.dev/docs/datatypes/datatypes_stream) _(for `zio.Task`)_.

## Usage

### Akka Streams _(neotypes-akka-stream)_

```scala mdoc:compile-only
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import neotypes.{GraphDatabase, StreamingDriver}
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._ // Brings the implicit Stream[AkkaStream] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

implicit val system = ActorSystem("QuickStart")

val driver = GraphDatabase.streamingDriver[AkkaStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

def query(driver: StreamingDriver[AkkaStream, Future]): Source[String, NotUsed] =
  "MATCH (p:Person) RETURN p.name".query[String].stream(driver)

val program: Future[Unit] = for {
  _ <- query(driver).runWith(Sink.foreach(println))
  _ <- driver.close
} yield ()

Await.ready(program, 5.seconds)
```

### FS2 _(neotypes-fs2-stream)_

#### With cats.effect.IO

```scala mdoc:compile-only
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global // Brings the implicit IORuntime instance into the scope.
import fs2.Stream
import neotypes.{GraphDatabase, StreamingDriver}
import neotypes.cats.effect.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2IOStream] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens

val driver: Resource[IO, StreamingDriver[Fs2IoStream, IO]] =
  GraphDatabase.streamingDriver[Fs2IoStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Stream[IO, Unit] =
  Stream.resource(driver).flatMap { d =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream(d)
      .evalMap(n => IO(println(n)))
  }

program.compile.drain.unsafeRunSync()
```

#### With other effect type

Basically the same code as above, but replacing **IO** with **F**
_(as long as there is an instance of `cats.effect.Async[F]`)_.
And replacing the `neotypes.fs2.Fs2IoStream` type alias with `neotypes.fs2.Fs2FStream[F]#T`.

### Monix Observables _(neotypes-monix-stream)_

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import neotypes.{GraphDatabase, StreamingDriver}
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.monix.stream.MonixStream
import neotypes.monix.stream.implicits._ // Brings the implicit Stream[MonixStream] instance into the scope.
import org.neo4j.driver.AuthTokens
import scala.concurrent.duration._

val driver: Resource[Task, StreamingDriver[MonixStream, Task]] =
  GraphDatabase.streamingDriver[MonixStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Observable[Unit] =
  Observable.fromResource(driver).flatMap { d =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream(d)
      .mapEval(n => Task(println(n)))
}

program.completedL.runSyncUnsafe(5.seconds)
```

### ZIO ZStreams _(neotypes-zio-stream)_

```scala mdoc:compile-only
import zio.{Runtime, Managed, Task}
import zio.stream.ZStream
import neotypes.{GraphDatabase, StreamingDriver}
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._ // Brings the implicit Stream[ZioStream] instance into the scope.
import org.neo4j.driver.AuthTokens

val driver: Managed[Throwable, StreamingDriver[ZioStream, Task]] =
  GraphDatabase.streamingDriver[ZioStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: ZStream[Any, Throwable, String] =
  ZStream.managed(driver).flatMap { s =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream(s)
  }

Runtime.default.unsafeRun(program.foreach(n => Task(println(n))))
```

-----

_Please note that the above provided type aliases are just for convenience_.

_You can always use:_

```scala mdoc:invisible
import cats.effect.IO
import neotypes.GraphDatabase
import neotypes.cats.effect.implicits._
import neotypes.fs2.implicits._
val uri: String = ""
def query: neotypes.DeferredQuery[String] = ???
```

* **_Type lambdas_:**

```scala mdoc:compile-only
val driver = GraphDatabase.streamingDriver[({ type T[A] = fs2.Stream[IO, A] })#T](uri)
```

* **_Type Alias_:**

```scala mdoc:compile-only
type Fs2Stream[T] = fs2.Stream[IO, T]
val driver = GraphDatabase.streamingDriver[Fs2Stream](uri)
```

* **[_Kind Projector_](https://github.com/typelevel/kind-projector):**

```scala
val driver = GraphDatabase.streamingDriver[fs2.Stream[IO, ?]](uri)
```

-----

The code snippets above are lazily retrieving data from **Neo4j**,
loading each element of the result only when it's requested
and commits the transaction once all elements are read.<br>
This approach aims to improve performance and memory footprint with large volumes of data.

## Transaction management

You have two options in transaction management:

+ **Manual**: if you use `neotypes.Transaction` and call `stream`,
you need to ensure that the transaction is gracefully closed after reading is finished.
+ **Auto-closing**: you may wish to automatically rollback the transaction once all elements are consumed.
This behavior is provided by `DeferredQuery.stream(StreamingDriver[S, F])`

## Alternative stream implementations

If you don't see your stream supported.
You can add your implementation of `neotypes.Stream.Aux[S[_], F[_]]` **typeclass**.
And add it to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect that will be used to produce each element retrieval.
* `S[_]` - the type of your stream.
