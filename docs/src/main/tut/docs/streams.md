---
layout: docs
title: "Streams"
---

# Streams

**neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream.
Currently, there are four implementations of streaming supported out of the box _(one for each effect type)_ -
[**Akka Streams**](https://doc.akka.io/docs/akka/current/stream/index.html) _(for `scala.concurrent.Future`)_,
[**FS2**](https://fs2.io/) _(for `cats.effect.Async[F]`)_,
[**Monix Observables**](https://monix.io/docs/3x/reactive/observable.html) _(for `monix.eval.Task`)_ &
[**ZIO ZStreams**](https://zio.dev/docs/datatypes/datatypes_stream) _(for `zio.Task`)_.

## Usage

### Akka Streams _(neotypes-akka-stream)_

```scala
import neotypes.GraphDatabase
import akka.stream.scaladsl.Source
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._ // Brings the implicit Stream[AkkaStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

implicit val system = ActorSystem("QuickStart")
implicit val materializer = ActorMaterializer()

val program: Source[String, Future[Unit]] = for {
  driver <- Source.fromFuture(GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****")))
  session <- Source.fromFuture(driver.session())
  data <- "match (p:Person) return p.name".query[String].stream[AkkaStream](session)
  _ <- Source.fromFuture(session.close())
  _ <- Source.fromFuture(driver.close())
} yield data

Await.ready(program.runWith(Sink.foreach(println)), 5 seconds)
```

### FS2 _(neotypes-fs2-stream)_

#### With cats.effect.IO

```scala
import cats.effect.{IO, Resource}
import neotypes.{GraphDatabase, Session}
import neotypes.cats.effect.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2IOStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val session: Resource[IO, Session[IO]] = for {
  driver <- GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Stream[IO, Unit] =
  Stream.resource(sesssion).flatMap { s =>
    "match (p:Person) return p.name"
      .query[String]
      .stream[Fs2IoStream](s)
      .evalMap(n => IO(println(n)))
  }

stream.compile.drain.unsafeRunSync()
```

#### With other effect type

Basically the same code as above, but replacing **IO** with **F**
_(as long as there is an instance of `cats.effect.Async[F]`)_.
And replacing the `neotypes.fs2.Fs2IoStream` type alias
with `neotypes.fs2.Fs2FStream[F]#T`.

### Monix Observables _(neotypes-monix-stream)_

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.monix.stream.MonixStream
import neotypes.monix.stream.implicits._ // Brings the implicit Stream[MonixStream] instance into the scope.
import scala.concurrent.duration._

val session: Resource[IO, Session[IO]] = for {
  driver <- GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Observable[String] =
  Observable.fromResource(session).flatMap { s =>
    "match (p:Person) return p.name"
      .query[String]
      .stream[MonixStream](s)
      .mapEval(n => Task(println(n)))
}

program.completedL.runSyncUnsafe(5 seconds)
```

### ZIO ZStreams _(neotypes-zio-stream)_

```scala
import zio.{DefaultRuntime, Managed Task}
import zio.stream.ZStream
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._ // Brings the implicit Stream[ZioStream] instance into the scope.

val runtime = new DefaultRuntime {}

val session: Managed[Throwable, Session] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: ZStream[Any, Throwable, String] =
  ZStream.managed(session).flatMap { s =>
    "match (p:Person) return p.name"
      .query[String]
      .stream[ZioStream](s)
  }

runtime.unsafeRun(program.foreach(n => Task(println(n))))
```

-----

_Please note that the above provided type aliases are just for convenience_.

_You can always use:_

* **_Type lambdas_:**

```scala
query.stream[{ type T[A] = fs2.Stream[IO, A] }#T](s)
```

* **_Type Alias_:**

```scala
type AkkaStream[T] = akka.stream.scaladsl.Source[T, Future[Unit]]
query.stream[AkkaStream](s)
```

* **[_Kind Projector_](https://github.com/typelevel/kind-projector):**

```scala
query.stream[fs2.Stream[F, ?]](s)
```

-----

The code snippets above are lazily retrieving data from neo4j, loading each element of the result only when it's requested and rolls back the transaction once all elements are read.
This approach aims to improve performance and memory footprint with large volumes of returning data.

## Transaction management

You have two options in transaction management:
* **Manual**: if you use `neotypes.Transaction` and call `stream`, you need to ensure that the transaction is gracefully closed after reading is finished.
* **Auto-closing**: you may wish to automatically rollback the transaction once
all elements are consumed. This behavior is provided by `DeferredQuery.stream(Session[F])`

## Alternative stream implementations

If you don't see your stream supported.
You can add your implementation of `neotypes.Stream.Aux[S[_], F[_]]` **typeclass**.
And add it to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect that will be used to produce each element retrieval.
* `S[_]` - the type of your stream.
