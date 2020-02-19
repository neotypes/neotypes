---
layout: docs
title: "Driver -> Session -> Transaction"
---

# Driver -> Session -> Transaction

These three classes are the main points of interactions with a **Neo4j** database.

A `Driver` is basically the connection with the Database. Usually, you would only need one instance per application, unless you need to connect to two different databases.<br>
A `Session` provides a context for performing operations _(`Transactions`)_ over the database. You may need as many as concurrent operations you want to have.
A `Transaction` is a logical container for an atomic unit of work. Only one transaction may exist in a `Session` at any point in time.

## Transaction managment

Each `Transaction` has to be started, used and finally either, committed or rolled back.

**neotypes** provides 3 ways of interacting with `Transactions`, designed for different use cases.

### Single query + automatic commit / rollback.

If you only need to perform one query, and want it to be automatically committed in case of success, or rolled back in case of failure.
You can use the `Session` directly.

```scala
val session: Session[F] = ???

val result: F[String] = "MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(session)
```

### Multiple queries + automatic commit / rollback.

Like the previous one, but with the possibility of executing multiple queries in the same transaction.
You can use `Session.transact` method.

```scala
val session: Session[F] = ???

val result: F[(String, String)] = session.transact { tx =>
  for {
    r1 <-"MATCH (p:Person {name: 'Charlize Theron'}) RETURN p.name".query[String].single(tx)
    r2 <-"MATCH (p:Person {name: 'Tom Hanks'}) RETURN p.name".query[String].single(tx)
  } yield (r1, r2)
}
```

> Note: under the hood, the previous method uses this one. Thus, they are equivalent for single-query transactions.

### Multiple queries + explicit commit / rollback.

If you want to control when to `commit` or `rollback` a `Transaction`.
You can use the `Session.transaction` method, to create an `F[Transaction[F]]`.

```scala
val session: Session[F] = ???
val transaction: F[Transaction[F]] = session.transaction

val result: F[Unit] = transaction.flatMap { tx =>
  for {
    r1 <-"CREATE (p: Person {name: 'Charlize Theron'})".query[Unit].execute(tx)
    r2 <-"CREATE (p:Person {name: 'Tom Hanks'})".query[Unit].execute(tx)
    _ <- tx.rollback // Nothing will be done.
  } yield ()
}
```
