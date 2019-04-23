---
layout: docs
title: "Side effect: Future/IO/Task"
---

# Side effect: Future/IO/Task

**neotypes** comes with three effect implementations:

### scala.concurrent.Future

```scala
import neotypes.Async._
import neotypes.implicits._
import scala.concurrent.Future

val s = driver.session().asScala[Future]

Await.result("match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s), 1 second)
```

### cats.effect.Async[F]

```scala
import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.implicits._

val s = driver.session().asScala[IO]

"match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s).unsafeRunSync()
```

### monix.eval.Task

```scala
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.monix.implicits._
import neotypes.implicits._
import scala.concurrent.duration._

val s = driver.session().asScala[Task]

"match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s).runSyncUnsafe(5 seconds)
```

## Custom side effect type
In order to support your implementation of side-effects,
you need to implement `neotypes.Async[YourIO]` and add it to the implicit scope.
