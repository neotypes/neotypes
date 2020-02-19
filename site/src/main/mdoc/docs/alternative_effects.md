---
layout: docs
title: "Side effects"
---

# Alternative effects: Future/IO/Task/ZIO

**neotypes** comes with four effect implementations:

### scala.concurrent.Future _(neotypes)_

```scala mdoc:compile-only
import neotypes.GraphDatabase
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.v1.AuthTokens

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
val session = driver.session

val program: Future[String] = for {
  data <- "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(session)
  _ <- session.close
  _ <- driver.close
} yield data

val data: String = Await.result(program, 1.second)
```

> Note: The previous example does not handle failures. Thus, it may leak resources.

### cats.effect.IO _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{IO, Resource}
import neotypes.{GraphDatabase, Session}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.v1.AuthTokens

val session: Resource[IO, Session[IO]] = for {
  driver <- GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: IO[String] = session.use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = program.unsafeRunSync()
```

### cats.effect.neotypes.Async[F] _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{Async, Resource}
import neotypes.{GraphDatabase, Session}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.v1.AuthTokens

def session[F[_] : Async]: Resource[F, Session[F]] = for {
  driver <- GraphDatabase.driver[F]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

def program[F[_] : Async]: F[String] = session[F].use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = program[cats.effect.IO].unsafeRunSync()
```

### monix.eval.Task _(neotypes-monix)_

```scala mdoc:compile-only
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.v1.AuthTokens

val session: Resource[Task, Session[Task]] = for {
  driver <- GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))
  session <- driver.session
} yield session

val program: Task[String] = session.use { s =>
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(s)
}

val data: String = program.runSyncUnsafe(1.second)
```

### zio.Task _(neotypes-zio)_

```scala mdoc:compile-only
import zio.{DefaultRuntime, Managed, Task}
import neotypes.{GraphDatabase, Session}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import org.neo4j.driver.v1.AuthTokens

val runtime = new DefaultRuntime {}

val session: Managed[Throwable, Session[Task]] = for {
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
