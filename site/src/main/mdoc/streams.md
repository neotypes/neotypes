---
layout: page
section: streams
title: "Streaming"
position: 50
---

# Streaming

**neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream.
Currently, there are four implementations of streaming supported out of the box _(one for each effect type)_ -
[**Akka Streams**](https://doc.akka.io/docs/akka/current/stream/index.html) _(for `scala.concurrent.Future`)_,
[**FS2**](https://fs2.io/) _(for `cats.effect.Async[F]`)_,
[**Monix Observables**](https://monix.io/docs/3x/reactive/observable.html) _(for `monix.eval.Task`)_ &
[**ZIO ZStreams**](https://zio.dev/docs/datatypes/datatypes_stream) _(for `zio.Task`)_.

## Usage

### Akka Streams _(neotypes-akka-stream)_

```scala mdoc:compile-only
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import neotypes.{GraphDatabase, Session}
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._ // Brings the implicit Stream[AkkaStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.v1.AuthTokens
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

implicit val system = ActorSystem("QuickStart")

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.session

def query(session: Session[Future]): Source[String, Future[Unit]] =
  "MATCH (p:Person) RETURN p.name".query[String].stream[AkkaStream](session)

val program: Future[Unit] = for {
  _ <- query(session).runWith(Sink.foreach(println))
  _ <- session.close
  _ <- driver.close
} yield ()

Await.ready(program, 5.seconds)
```

### FS2 _(neotypes-fs2-stream)_

#### With cats.effect.IO

```scala mdoc:compile-only
import cats.effect.{IO, Resource}
import fs2.Stream
import neotypes.{GraphDatabase, Session}
import neotypes.cats.effect.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2IOStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.v1.AuthTokens

val session: Resource[IO, Session[IO]] = for {
  driver <- GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Stream[IO, Unit] =
  Stream.resource(session).flatMap { s =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream[Fs2IoStream](s)
      .evalMap(n => IO(println(n)))
  }

program.compile.drain.unsafeRunSync()
```

#### With other effect type

Basically the same code as above, but replacing **IO** with **F**
_(as long as there is an instance of `cats.effect.Async[F]`)_.
And replacing the `neotypes.fs2.Fs2IoStream` type alias
with `neotypes.fs2.Fs2FStream[F]#T`.

### Monix Observables _(neotypes-monix-stream)_

```scala mdoc:compile-only
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
import org.neo4j.driver.v1.AuthTokens
import scala.concurrent.duration._

val session: Resource[Task, Session[Task]] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Observable[Unit] =
  Observable.fromResource(session).flatMap { s =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream[MonixStream](s)
      .mapEval(n => Task(println(n)))
}

program.completedL.runSyncUnsafe(5.seconds)
```

### ZIO ZStreams _(neotypes-zio-stream)_

```scala mdoc:compile-only
import zio.{Runtime, Managed, Task}
import zio.stream.ZStream
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._ // Brings the implicit Stream[ZioStream] instance into the scope.
import org.neo4j.driver.v1.AuthTokens

val runtime = Runtime.default

val session: Managed[Throwable, Session[Task]] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: ZStream[Any, Throwable, String] =
  ZStream.managed(session).flatMap { s =>
    "MATCH (p:Person) RETURN p.name"
      .query[String]
      .stream[ZioStream](s)
  }

runtime.unsafeRun(program.foreach(n => Task(println(n))))
```

-----

_Please note that the above provided type aliases are just for convenience_.

_You can always use:_

```scala mdoc:invisible
import cats.effect.IO
import neotypes.implicits.all._
import neotypes.cats.effect.implicits._
import neotypes.fs2.implicits._
def query: neotypes.DeferredQuery[String] = ???
def session: neotypes.Session[IO] = ???
```

* **_Type lambdas_:**

```scala mdoc:compile-only
query.stream[({ type T[A] = fs2.Stream[IO, A] })#T](session)
```

* **_Type Alias_:**

```scala mdoc:compile-only
type Fs2Stream[T] = fs2.Stream[IO, T]
query.stream[Fs2Stream](session)
```

* **[_Kind Projector_](https://github.com/typelevel/kind-projector):**

```scala
query.stream[fs2.Stream[IO, ?]](session)
```

-----

The code snippets above are lazily retrieving data from neo4j, loading each element of the result only when it's requested and rolls back the transaction once all elements are read.
This approach aims to improve performance and memory footprint with large volumes of returned data.

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
