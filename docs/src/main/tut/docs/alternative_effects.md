---
layout: docs
title: "Side effects"
---

# Side effects: Future/IO/Task/ZIO

**neotypes** comes with four effect implementations:

### scala.concurrent.Future

```scala
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.Future

val s = driver.session().asScala[Future]

Await.result("match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s), 1 second)
```

### cats.effect.Async[F]

```scala
import cats.effect.IO
import neotypes.cats.implicits._ // Brings the implicit Async[IO] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val s = driver.session().asScala[IO]

"match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s).unsafeRunSync()
```

### monix.eval.Task

```scala
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit Async[Task] instance into the scope.
import scala.concurrent.duration._

val s = driver.session().asScala[Task]

"match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s).runSyncUnsafe(5 seconds)
```

### zio.Task

```scala
import zio.Task
import zio.DefaultRuntime
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit Async[Task] instance into the scope.

val runtime = new DefaultRuntime {

val s = driver.session().asScala[Task]

runtime.unsafeRun("match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s))
```

## Custom side effect type
In order to support your implementation of side-effects,
you need to implement `neotypes.Async[YourIO]` and add it to the implicit scope.
