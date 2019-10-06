---
layout: docs
title: "Side effects"
---

# Alternative effects: Future/IO/Task/ZIO

**neotypes** comes with four effect implementations:

### scala.concurrent.Future _(neotypes)_

```scala
import neotypes.GraphDatabase
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

val driver: Driver[Future] = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session: Session[Future] = driver.session

val program: Future[String] = for {
  data <- "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(session)
  _ <- session.close()
  _ <- driver.close()
} yield data

val data: String = Await.result(program, 1 second)
```

> Note: The previous example does not handle failures. Thus, it may leak resources.

### cats.effect.neotypes.Async[F] _(neotypes-cats-effect)_

```scala
import cats.effect.{IO, Resource}
import neotypes.{GraphDatabase, Session}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

val session: Resource[IO, Session[IO]] = for {
  driver <- GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: IO[String] = session.use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = program.unsafeRunSync()
```

### monix.eval.Task _(neotypes-monix)_

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import scala.concurrent.duration._

val session: Resource[Task, Session[Task]] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Task[String] = session.use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = program.runSyncUnsafe(5 seconds)
```

### zio.Task _(neotypes-zio)_

```scala
import zio.{DefaultRuntime, Managed, Task}
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.session._ // Provides the asScala[F[_]] extension method.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.

val runtime = new DefaultRuntime {}

val session: Managed[Throwable, Session] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Task[String] = session.use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = runtime.unsafeRun(program)
```

## Custom effect type
In order to support your any other effect type,
you need to implement the `neotypes.Async.Aux[F[_], R[_]]` **typeclasses**
and add them to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect type.
* `R[_]` - the resource used to wrap the creation of Drivers & Sessions.
