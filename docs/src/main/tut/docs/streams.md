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

### Akka Streams

```scala
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._ // Brings the implicit Stream[AkkaStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val session = driver.session().asScala[Future]
implicit val system = ActorSystem("QuickStart")
implicit val materializer = ActorMaterializer()

"match (p:Person) return p.name"
  .query[String]
  .stream[AkkaStream](session)
  .runWith(Sink.foreach(println))
```

### FS2

#### With cats.effect.IO

```scala
import cats.effect.IO
import neotypes.cats.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2IOStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val s = driver.session().asScala[IO]

"match (p:Person) return p.name"
  .query[String]
  .stream[Fs2IoStream](s)
  .evalMap(n => IO(println(n)))
  .compile
  .drain
  .unsafeRunSync()
```

#### With other effect type.

```scala
import neotypes.cats.implicits._ // Brings the implicit Async[F[_]] instance into the scope.
import neotypes.fs2.Fs2FStream
import neotypes.fs2.implicits._ // Brings the implicit Stream[Fs2FStream] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

type F[_] = ??? // As long as there is an instance of cats.effect.Async[F].

val s = driver.session().asScala[F]

"match (p:Person) return p.name"
  .query[String]
  .stream[Fs2FStream[F]#T](s)
  .evalMap(n => F.delay(println(n)))
  .compile
  .drain
```

### Monix Observables

```scala
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.monix.stream.MonixStream
import neotypes.monix.stream.implicits._ // Brings the implicit Stream[MonixStream] instance into the scope.
import scala.concurrent.duration._

val s = driver.session().asScala[Task]

"match (p:Person) return p.name"
  .query[String]
  .stream[MonixStream](s)
  .mapEval(n => Task(println(n)))
  .completedL
  .runSyncUnsafe(5 seconds)
```

### ZIO ZStreams

```scala
import zio.Task
import zio.DefaultRuntime
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._ // Brings the implicit Stream[ZioStream] instance into the scope.

val runtime = new DefaultRuntime {}

val s = driver.session().asScala[Task]

runtime.unsafeRun(
  "match (p:Person) return p.name"
    .query[String]
    .stream[ZioStream](s)
    .foreach(n => Task(println(n)))
)
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
You can add your implementation of `neotypes.Stream.Aux[S[_], F[_]]` typeclass,
and add it to the implicit scope.

The type parameters in the signature indicate:

* `S[_]` - a type of your stream.
* `F[_]` - an effect that will be used to produce each element retrieval.
