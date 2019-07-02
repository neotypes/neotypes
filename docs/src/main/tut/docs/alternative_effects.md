---
layout: docs
title: "Side effects"
---

# Side effects: Future/IO/Task/ZIO

**neotypes** comes with four effect implementations:

### scala.concurrent.Future

```scala
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val program: Future[String] = for {
  driver <- GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session()
  data <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(session)
  _ <- session.close()
  _ <- driver.close()
} yield data

val data: String = Await.result(program, 1 second)
```

### cats.effect.neotypes.Async[F]

```scala
import cats.effect.{IO, Resource}
import neotypes.Session
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val session: Resource[IO, Session[IO]] = for {
  driver <- Resource.make(
    GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  ) { driver => driver.close() }

  session <- Resource.make(
    driver.session()
  ) { session => session.close() }
} yield session

val program: IO[String] = session.use { s =>
  "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
}

val data: String = program.unsafeRunSync()
```

### monix.eval.Task

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.Session
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import scala.concurrent.duration._

val session: Resource[Task, Session[Task]] = for {
  driver <- Resource.make(
    GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  ) { driver => driver.close() }

  session <- Resource.make(
    driver.session()
  ) { session => session.close() }
} yield session

val program: Task[String] = session.use { s =>
  "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
}

val data: String = program.runSyncUnsafe(5 seconds)
```

### zio.Task

```scala
import zio.{DefaultRuntime, Managed, Task}
import neotypes.Session
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.

val runtime = new DefaultRuntime {}

val session: Managed[Throwable, Session] = for {
  driver <- Managed.make(
    GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  ) { driver => driver.close() }

  session <- Managed.make(
    driver.session()
  ) { session => session.close() }
} yield session

val program: Task[String] = session.use { s =>
  "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
}

val data: String = runtime.unsafeRun(program)
```

## Custom side effect type
In order to support your implementation of side-effects,
you need to implement `neotypes.neotypes.Async[YourIO]` and add it to the implicit scope.
