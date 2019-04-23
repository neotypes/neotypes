---
layout: docs
title: "Streams"
---

# Streams

**neotypes** allows to stream large results by lazily consuming the result and putting elements into a stream.
Currently, there are three implementations of streaming supported out of the box _(one for each effect type)_ -
[**Akka Streams**](https://doc.akka.io/docs/akka/current/stream/index.html) _(for `scala.concurrent.Future`)_,
[**FS2**](https://fs2.io/) _(for `cats.effect.Async[F]`)_
& [**Monix Observables**](https://monix.io/docs/3x/reactive/observable.html) _(for `monix.eval.Task`)_.

## Usage

### Akka Streams

```scala
import neotypes.akkastreams.AkkaStream
import neotypes.akkastreams.implicits._
import neotypes.implicits._

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
import neotypes.cats.implicits._
import neotypes.fs2.Fs2IoStream
import neotypes.fs2.implicits._
import neotypes.implicits._

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
import neotypes.cats.implicits._
import neotypes.fs2.Fs2FStream
import neotypes.fs2.implicits._
import neotypes.implicits._

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
import monix.execution.Scheduler.Implicits.globa
import neotypes.monix.implicits._
import neotypes.monix.stream.MonixStream
import neotypes.monix.stream.implicits._
import neotypes.implicits._
import scala.concurrent.duration._

val s = driver.session().asScala[Task]

"match (p:Person) return p.name"
  .query[String]
  .stream[MonixStream](s)
  .mapEval(n => Task(println(n)))
  .completedL
  .runSyncUnsafe(5 seconds)
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
