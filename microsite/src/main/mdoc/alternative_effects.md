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
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.syntax.all._ // Provides the query extension method.
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.AuthTokens

val driver = GraphDatabase.asyncDriver[Future]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Future[String] = for {
  data <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
    .query(ResultMapper.string)
    .single(driver)
  _ <- driver.close
} yield data

val data: String = Await.result(program, 1.second)
```

> Note: The previous example does not handle failures. Thus, it may leak resources.

### cats.effect.IO _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import neotypes.{AsyncDriver, GraphDatabase}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.syntax.all._ // Provides the query extension method.
import org.neo4j.driver.AuthTokens

val driver: Resource[IO, AsyncDriver[IO]] =
  GraphDatabase.asyncDriver[IO]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: IO[String] = driver.use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
    .query(ResultMapper.string)
    .single(d)
}

val data: String = program.unsafeRunSync()
```

### cats.effect.neotypes.Async[F] _(neotypes-cats-effect)_

```scala mdoc:compile-only
import cats.effect.{Async, IO, Resource}
import cats.effect.unsafe.implicits.global
import neotypes.{AsyncDriver, GraphDatabase}
import neotypes.cats.effect.implicits._ // Brings the implicit neotypes.Async[IO] instance into the scope.
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.syntax.all._ // Provides the query extension method.
import org.neo4j.driver.AuthTokens

def driver[F[_] : Async]: Resource[F, AsyncDriver[F]] =
  GraphDatabase.asyncDriver[F]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

def program[F[_] : Async]: F[String] = driver[F].use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
    .query(ResultMapper.string)
    .single(d)
}

val data: String = program[IO].unsafeRunSync()
```

### monix.eval.Task _(neotypes-monix)_

```scala
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.{AsyncDriver, GraphDatabase}
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.monix.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import neotypes.syntax.all._ // Provides the query extension method.
import scala.concurrent.duration._ // Provides the second extension method.
import org.neo4j.driver.AuthTokens

val driver: Resource[Task, AsyncDriver[Task]] =
  GraphDatabase.asyncDriver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Task[String] = driver.use { d =>
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
    .query(ResultMapper.string)
    .single(d)
}

val data: String = program.runSyncUnsafe(1.second)
```

### zio.Task _(neotypes-zio)_

```scala mdoc:compile-only
import neotypes.{AsyncDriver, GraphDatabase}
import neotypes.mappers.ResultMapper // Allows to decode query results.
import neotypes.syntax.all._ // Provides the query extension method.
import neotypes.zio.implicits._ // Brings the implicit neotypes.Async[Task] instance into the scope.
import org.neo4j.driver.AuthTokens
import zio.{Runtime, Scope, Task, Unsafe, ZIO}

val driver: ZIO[Scope, Throwable, AsyncDriver[Task]] =
  GraphDatabase.asyncDriver[Task]("bolt://localhost:7687", AuthTokens.basic("neo4j", "****"))

val program: Task[String] = ZIO.scoped {
  driver.flatMap {d =>
    "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
      .query(ResultMapper.string)
      .single(d)
  }
}

val data: String = Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(program).getOrThrow()
}
```

## Custom effect type
In order to support your any other effect type,
you need to implement the `neotypes.Async.Aux[F[_], R[_]]` **typeclasses**
and add them to the implicit scope.

The type parameters in the signature indicate:

* `F[_]` - the effect type.
* `R[_]` - the resource used to wrap the creation of Drivers.
