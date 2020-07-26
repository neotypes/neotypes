---
layout: page
section: transaction
title: "Transactions"
position: 100
---

# Driver -> Session -> Transaction

These three classes are the main points of interactions with a **Neo4j** database.

A `Driver` is basically the connection with the Database. Usually, you would only need one instance per application, unless you need to connect to two different databases.<br>
A `Session` provides a context for performing operations _(`Transactions`)_ over the database. You may need as many as concurrent operations you want to have.
A `Transaction` is a logical container for an atomic unit of work. Only one transaction may exist in a `Session` at any point in time.

## Session thread safety

Unlike its **Java** counterpart, `neotypes.Session` is thread safe.
We achieve that by using a simple locking mechanism that ensures only one `Transaction` can be created `Session`.
If you want / need to run multiple queries concurrently then you need to create multiple `Sessions` and load-balance the petitions across them yourself.

> **Note**: For all _pure_ effects, the blocking of the locks is semantic _(meaning no real thread was blocked)_.
For `Future` we do a _best effort_ by using `scala.concurrent.blocking` to notify the EC that the following action will block.

## Transaction management

Each `Transaction` has to be started, used and finally, either committed or rolled back.

**neotypes** provides 3 ways of interacting with `Transactions`, designed for different use cases.

### Single query + automatic commit / rollback.

If you only need to perform one query, and want it to be automatically committed in case of success, or rolled back in case of failure.
You can use the `Session` directly.

```scala mdoc:invisible
type F[A] = scala.concurrent.Future[A]
import scala.concurrent.ExecutionContext.Implicits.global
```

```scala mdoc:compile-only
import neotypes.Session
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMapper[String] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(session: Session[F]): F[String] =
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name"
    .query[String]
    .single(session)
```

### Multiple queries + automatic commit / rollback.

Like the previous one, but with the possibility of executing multiple queries in the same transaction.
You can use `Session.transact` method.

```scala mdoc:compile-only
import neotypes.Session
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMappers instances in scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(session: Session[F]): F[(String, String)] = session.transact { tx =>
  for {
    r1 <-"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(tx)
    r2 <-"MATCH (p:Person {name: 'Tom Hanks'}) RETURN p.name".query[String].single(tx)
  } yield (r1, r2)
}
```

> **Note**: under the hood, the previous method uses this one. Thus, they are equivalent for single-query transactions.

### Multiple queries + explicit commit / rollback.

If you want to control when to `commit` or `rollback` a `Transaction`.
You can use the `Session.transaction` method, to create an `F[Transaction[F]]`.

```scala mdoc:compile-only
import neotypes.Session
import neotypes.implicits.mappers.executions._ // Brings the implicit ExecutionMapper[Unit] instance into the scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def result(session: Session[F]): F[Unit] = session.transaction.flatMap { tx =>
  for {
    _ <-"CREATE (p: Person {name: 'Charlize Theron'})".query[Unit].execute(tx)
    _ <-"CREATE (p:Person {name: 'Tom Hanks'})".query[Unit].execute(tx)
    _ <- tx.rollback // Nothing will be done.
  } yield ()
}
```

> **Note**: It is mandatory to only call either `commit` or `rollback` once.
Calling both or one of them but more than once will leave the system in an unstable state.
_(probably an error or a deadlock)_

### Transaction configuration.

If you want to configure the timeout of a `Transaction` or add add custom metadata to it, you can use a custom [`TransactionConfig`](https://neo4j.com/docs/api/java-driver/current/org/neo4j/driver/TransactionConfig.html).

**neotypes** provides a Scala friendly factory for creating instances of `TransactionConfig`.


```scala mdoc
import neotypes.TransactionConfig
import neotypes.implicits.mappers.parameters._
import neotypes.types.QueryParam
import scala.concurrent.duration._

val config = TransactionConfig(
  timeout = 2.seconds,
  metadata = Map("foo" -> QueryParam("bar"), "baz" -> QueryParam(10))
)
```

Which you can use in operations that explicitly or implicitly create `Transactions`.


```scala mdoc:compile-only
import neotypes.{Session, Transaction}
import neotypes.implicits.mappers.results._ // Brings the implicit ResultMappers instances in scope.
import neotypes.implicits.syntax.string._ // Provides the query[T] extension method.

def customTransaction(session: Session[F]): F[Transaction[F]] =
  session.transaction(config)

def result1(session: Session[F]): F[(String, String)] = session.transact(config) { tx =>
  for {
    r1 <-"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(tx)
    r2 <-"MATCH (p:Person {name: 'Tom Hanks'}) RETURN p.name".query[String].single(tx)
  } yield (r1, r2)
}

def result2(session: Session[F]): F[String] =
  "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name"
    .query[String]
    .single(session, config)
```
