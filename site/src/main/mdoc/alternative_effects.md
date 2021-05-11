---
layout: page
section: effects
title: "Alternative effects"
position: 40
---

# Alternative effects

**neotypes** comes with four effect implementations: **Future**, **cats-effect**, **Monix** & **ZIO**.

### scala.concurrent.Future _(neotypes)_

```scala mdoc:compile-only
import neotypes.GraphDatabase
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.AuthTokens

val driver = GraphDatabase.driver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Future[String] = for {
  data <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(driver)
  _ <- driver.close
} yield data

val data: String = Await.result(program, 1.second)
```

> Note: The previous example does not handle failures. Thus, it may leak resources.

### cats.effect.IO _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global // Brings the implicit IORuntime instance into the scope.
import neotypes.{GraphDatabase, Driver}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens

val driver: Resource[IO, Driver[IO]] =
  GraphDatabase.driver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: IO[String] = driver.use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(d)
}

val data: String = program.unsafeRunSync()
```

### cats.effect.neotypes.Async[F] _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{Async, IO, Resource}
import cats.effect.unsafe.implicits.global // Brings the implicit IORuntime instance into the scope.
import neotypes.{GraphDatabase, Driver}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import org.neo4j.driver.AuthTokens

def driver[F[_] : Async]: Resource[F, Driver[F]] =
  GraphDatabase.driver[F]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

def program[F[_] : Async]: F[String] = driver[F].use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(d)
}

val data: String = program[IO].unsafeRunSync()
```

### monix.eval.Task _(neotypes-monix)_

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.{GraphDatabase, Driver}
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.monix.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.AuthTokens

val driver: Resource[Task, Driver[Task]] =
  GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Task[String] = driver.use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(d)
}

val data: String = program.runSyncUnsafe(1.second)
```

### zio.Task _(neotypes-zio)_

```scala mdoc:compile-only
import zio.{Runtime, Managed, Task}
import neotypes.{GraphDatabase, Driver}
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.
import neotypes.zio.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import org.neo4j.driver.AuthTokens

val driver: Managed[Throwable, Driver[Task]] =
  GraphDatabase.driver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Task[String] = driver.use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(d)
}

val runtime = Runtime.default
val data: String = runtime.unsafeRun(program)
```

## Custom effect type
In order to support your any other effect type,
you need to implement the `neotypes.Async.Aux[F[_], R[_]]` **typeclasses**
and add them to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect type.
* `R[_]` - the resource used to wrap the creation of Drivers.
