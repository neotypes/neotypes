---
layout: page
section: transaction
title: "Transactions"
position: 60
---

# Driver -> Transaction

These two classes are the main points of interactions with a **Neo4j** database.

A `Driver` is basically the connection with the Database,
and provides a context for performing operations _(`Transaction`s)_ over the database.
Usually, you would only need one instance per application.<br>
A `Transaction` is a logical container for an atomic unit of work.
A single `Driver` can start multiple concurrent `Transaction`s.

> **Note**: Like its **Java** counterpart,
> `neotypes.Driver` is thread safe but `neotypes.Transaction` is not.

## Transaction management

Each `Transaction` has to be started, used and finally, either committed or rolled back.

**neotypes** provides 3 ways of interacting with `Transaction`s, designed for different use cases.

### Single query + automatic commit / rollback.

If you only need to perform one query, and want it to be automatically committed in case of success, or rolled back in case of failure.
You can use the `Driver` directly.

```scala mdoc:invisible
type F[A] = scala.concurrent.Future[A]
import scala.concurrent.ExecutionContext.Implicits.global
```

```scala mdoc:compile-only
import neotypes.Driver
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(driver: Driver[F]): F[String] =
  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name"
    .query[String]
    .single(driver)
```

### Multiple queries + automatic commit / rollback.

Like the previous one, but with the possibility of executing multiple queries in the same `Transaction`.
You can use `Driver.transact` method.

```scala mdoc:compile-only
import neotypes.Driver
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(driver: Driver[F]): F[(String, String)] = driver.transact { tx =>
  for {
    r1 <-"MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].single(tx)
    r2 <-"MATCH (p: Person { name: 'Tom Hanks' }) RETURN p.name".query[String].single(tx)
  } yield (r1, r2)
}
```

> **Note**: under the hood, the previous method uses this one. Thus, they are equivalent for single-query operations.

### Multiple queries + explicit commit / rollback.

If you want to control when to `commit` or `rollback` a `Transaction`.
You can use the `Driver.transaction` method, to create an `F[Transaction[F]]`.

```scala mdoc:compile-only
import neotypes.Driver
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(driver: Driver[F]): F[Unit] = driver.transaction.flatMap { tx =>
  for {
    _ <-"CREATE (p: Person { name: 'Charlize Theron' })".query[Unit].execute(tx)
    _ <-"CREATE (p: Person { name: 'Tom Hanks' })".query[Unit].execute(tx)
    _ <- tx.rollback // Nothing will be done.
  } yield ()
}
```

> **Note**: It is mandatory to only call either `commit` or `rollback` once.
> Calling both or one of them but more than once will leave the system in an undefined state.
> _(probably an error or a deadlock)_

## Transaction configuration.

You can configure the timeout, access mode, database and metadata of a `Transaction`
Using a custom [`TransactionConfig`](https://neotypes.github.io/neotypes/api/neotypes/TransactionConfig.html)

```scala mdoc
import neotypes.TransactionConfig
import neotypes.types.QueryParam
import scala.concurrent.duration._

val config =
  TransactionConfig
    .default
    .withTimeout(2.seconds)
    .withMetadata(Map("foo" -> QueryParam("bar"), "baz" -> QueryParam(10)))
```

Which you can use in operations that explicitly or implicitly create `Transaction`s.

```scala mdoc:compile-only
import neotypes.{Driver, Transaction}
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def customTransaction(driver: Driver[F]): F[Transaction[F]] =
  driver.transaction(config)

def result1(driver: Driver[F]): F[(String, String)] = driver.transact(config) { tx =>
  for {
    r1 <-"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(tx)
    r2 <-"MATCH (p:Person {name: 'Tom Hanks'}) RETURN p.name".query[String].single(tx)
  } yield (r1, r2)
}

def result2(driver: Driver[F]): F[String] =
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name"
    .query[String]
    .single(driver, config)
```
