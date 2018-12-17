---
layout: docs
title: "Supported types"
---

## Supported types

{:.table}
| Type                                      | Query result   | Field of a case class | Query parameter  |
| ----------------------------------------- |:--------------:| :--------------------:|:-----------------|
| `scala.Int                             `  | ✓              |✓                     |✓|
| `scala.Long                            `  | ✓              |✓                     |✓|
| `scala.Double                          `  | ✓              |✓                     |✓|
| `scala.Float                           `  | ✓              |✓                     |✓|
| `java.lang.String                      `  | ✓              |✓                     |✓|
| `scala.Option[T]                       `  | ✓              |✓                     |✓|
| `scala.Boolean                         `  | ✓              |✓                     |✓`*`|
| `scala.Array[Byte]                     `  | ✓              |✓                     |✓|
| `scala.Map[String, T: ValueMapper]     `  | ✓              |                      |✓`**`|
| `java.time.LocalDate                   `  | ✓              |✓                     |✓|
| `java.time.LocalTime                   `  | ✓              |✓                     |✓|
| `java.time.LocalDateTime               `  | ✓              |✓                     |✓|
| `org.neo4j.driver.v1.Value             `  | ✓              |                      ||
| `org.neo4j.driver.v1.types.Node        `  | ✓              |✓                     ||
| `org.neo4j.driver.v1.types.Relationship`  | ✓              |✓                     ||
| `shapeless.HList                       `  | ✓              |                      ||
| `neotypes.types.Path                   `  | ✓              |                      ||
| `Tuple (1-22)                          `  | ✓              |                      ||
| `User defined case class               `  | ✓              |                      ||

* `*` - `None` is converted into `null`
* `**` - scala.Map[String, Any] 