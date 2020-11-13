---
layout: page
section: streams
title: "Streaming"
position: 50
---

# Streaming

**neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream.
Currently, there are four implementations of streaming supported out of the box _(one for each effect type)_:

+ [**Akka Streams**](https://doc.akka.io/docs/akka/current/stream/index.html) _(for `scala.concurrent.Future`)_.
+ [**FS2**](https://fs2.io/) _(for `cats.effect.Async[F]`)_.
+ [**Monix Observables**](https://monix.io/docs/3x/reactive/observable.html) _(for `monix.eval.Task`)_.
+ [**ZIO ZStreams**](https://zio.dev/docs/datatypes/datatypes_stream) _(for `zio.Task`)_.

Since `0.16.0` _streaming_ is provided using the new `ReactiveSession`,
that was added on the `v4` version of the **Java** driver.
As such, `stream` is not longer an operation provided by a normal `Session`,
but rather you need to create and use a new `StreamingSession`.

> Note: This means that when using a `StreamingSession` all operations
> _(including not streaming ones like `single`)_
> are implemented in terms of `ReactiveStreams`.

## Usage

### Akka Streams _(neotypes-akka-stream)_

```scala mdoc:compile-only
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import neotypes.GraphDatabase
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._ // Brings the implicit Stream[AkkaStream] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

implicit val system = ActorSystem("QuickStart")

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.streamingSession[AkkaStream]

val data: Source[String, NotUsed] = session.flatMapConcat { s =>
  "MATCH (p:Person) RETURN p.name".query[String].stream(s)
}

val program: Future[Unit] = for {
  _ <- data.runWith(Sink.foreach(println))
  _ <- driver.close
} yield ()

Await.ready(program, 5.seconds)
```

### FS2 _(neotypes-fs2-stream)_

#### With cats.effect.IO

```scala mdoc:compile-only
import cats.effect.IO
import fs2.Stream
import neotypes.GraphDatabase
import neotypes.cats.effect.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2IOStream] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens

implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

val driverR = GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val data: Stream[IO, String] = for {
  driver <- Stream.resource(driverR)
  session <- driver.streamingSession[Fs2IoStream]
  name <- "MATCH (p:Person) RETURN p.name".query[String].stream(session)
} yield name

val program: IO[Unit] = data.evalMap(n => IO(println(n))).compile.drain

program.unsafeRunSync()
```

#### With other effect type

Basically the same code as above, but replacing **IO** with **F**
_(as long as there is an instance of `cats.effect.Async[F]`)_.
And replacing the `neotypes.fs2.Fs2IoStream` type alias
with `neotypes.fs2.Fs2FStream[F]#T`.

### Monix Observables _(neotypes-monix-stream)_

```scala mdoc:compile-only
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import neotypes.GraphDatabase
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.monix.stream.MonixStream
import neotypes.monix.stream.implicits._ // Brings the implicit Stream[MonixStream] instance into the scope.
import org.neo4j.driver.AuthTokens
import scala.concurrent.duration._

val driverR = GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val data: Observable[String] = for {
  driver <- Observable.fromResource(driverR)
  session <- driver.streamingSession[MonixStream]
  name <- "MATCH (p:Person) RETURN p.name".query[String].stream(session)
} yield name

val program: Task[Unit] = data.mapEval(n => Task(println(n))).completedL

program.runSyncUnsafe(5.seconds)
```

### ZIO ZStreams _(neotypes-zio-stream)_

```scala mdoc:compile-only
import zio.{Runtime, Task}
import zio.stream.ZStream
import neotypes.GraphDatabase
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._ // Brings the implicit Stream[ZioStream] instance into the scope.
import org.neo4j.driver.AuthTokens

val runtime = Runtime.default

val driverM = GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val data: ZStream[Any, Throwable, String] = for {
  driver <- ZStream.managed(driverM)
  session <- driver.streamingSession[ZioStream]
  name <- "MATCH (p:Person) RETURN p.name".query[String].stream(session)
} yield name

val program: Task[Unit] = data.foreach(n => Task(println(n)))

runtime.unsafeRun(program)
```

-----

_Please note that the above provided type aliases are just for convenience_.

_You can always use:_

```scala mdoc:invisible
import cats.effect.IO
import neotypes.cats.effect.implicits._
import neotypes.fs2.implicits._
implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)
def query: neotypes.DeferredQuery[String] = ???
def driver: neotypes.Driver[IO] = ???
```

* **_Type lambdas_:**

```scala mdoc:compile-only
val session = driver.streamingSession[({ type T[A] = fs2.Stream[IO, A] })#T]
session.flatMap(s => query.stream(s))
```

* **_Type Alias_:**

```scala mdoc:compile-only
type Fs2Stream[T] = fs2.Stream[IO, T]
val session = driver.streamingSession[Fs2Stream]
session.flatMap(s => query.stream(s))
```

* **[_Kind Projector_](https://github.com/typelevel/kind-projector):**

```scala
val session = driver.streamingSession[fs2.Stream[IO, ?]]
session.flatMap(s => query.stream(s))
```

-----

The code snippets above are lazily retrieving data from neo4j, loading each element of the result only when it's requested and commits the transaction once all elements are read.
This approach aims to improve performance and memory footprint with large volumes of returned data.

## Transaction management

You have two options in transaction management:
* **Manual**: if you use `neotypes.StreamingTransaction[F, S]` and call `stream`, you need to ensure that the transaction is gracefully closed after reading is finished.
* **Auto-closing**: you may wish to automatically rollback the transaction once
all elements are consumed. This behavior is provided by `DeferredQuery.stream(StreamingSession[F, S])`

## Alternative stream implementations

If you don't see your stream supported.
You can add your implementation of `neotypes.Stream.Aux[S[_], F[_]]` **typeclass**.
And add it to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect that will be used to produce each element retrieval.
* `S[_]` - the type of your stream.
