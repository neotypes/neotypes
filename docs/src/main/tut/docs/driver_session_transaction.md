---
layout: docs
title: "Driver -> Session -> Transaction"
---

# Driver -> Session -> Transaction

## Transaction

A transaction can be obtained in two ways:

1. `val tx = session.beginTransaction()`. In this case, transaction lifecycle (commit/rollback) should be managed by your application code.
2. `session.transact(tx => ...)` commit/rollback will be managed by **neotypes**.

You can chain multiple distinct queries into one transaction by providing the same instance of `neotypes.Transaction` to query executors.

```scala
val tx = ???

for {
 id <- "match (p:Person {name: 'Charlize Theron'}) return p.id".query[Long].single(tx)
 _ <- c"create (p:Record {personId: $id)".query[Unit].execute(tx)
} yield ()
```
