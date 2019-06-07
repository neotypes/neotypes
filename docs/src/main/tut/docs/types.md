---
layout: docs
title: "Supported types"
---

## Supported types

{:.table}
| Type                                      | Query result   | Field of a case class | Query parameter |
| ----------------------------------------- |:--------------:|:---------------------:|:-----------------|
| `scala.Boolean                         `  | ✓              |✓                      |✓|
| `scala.Int                             `  | ✓              |✓                      |✓|
| `scala.Long                            `  | ✓              |✓                      |✓|
| `scala.Double                          `  | ✓              |✓                      |✓|
| `scala.Float                           `  | ✓              |✓                      |✓|
| `java.lang.String                      `  | ✓              |✓                      |✓|
| `scala.Array[Byte]                     `  | ✓              |✓                      |✓|
| `scala.Option[T]                       `  | ✓              |✓                      |✓`*`|
| `scala.List[T: ValueMapper]            `  | ✓              |✓                      |✓|
| `scala.Set[T: ValueMapper]             `  | ✓              |✓                      |✓|
| `scala.Map[String, T: ValueMapper]     `  | ✓              |✓                      |✓`**`|
| `java.time.LocalDate                   `  | ✓              |✓                      |✓|
| `java.time.LocalDateTime               `  | ✓              |✓                      |✓|
| `java.time.LocalTime                   `  | ✓              |✓                      |✓|
| `java.time.OffsetDateTime              `  | ✓              |✓                      |✓|
| `java.time.OffsetTime                  `  | ✓              |✓                      |✓|
| `java.time.ZonedDateTime               `  | ✓              |✓                      |✓|
| `java.util.UUID                        `  | ✓              |✓                      |✓|
| `org.neo4j.driver.v1.Value             `  | ✓              |✓                      ||
| `org.neo4j.driver.v1.types.IsoDuration `  | ✓              |✓                      ||
| `org.neo4j.driver.v1.types.Node        `  | ✓              |✓                      ||
| `org.neo4j.driver.v1.types.Point       `  | ✓              |✓                      ||
| `org.neo4j.driver.v1.types.Relationship`  | ✓              |✓                      ||
| `shapeless.HList                       `  | ✓              |                       ||
| `neotypes.types.Path                   `  | ✓              |                       ||
| `Tuple (1-22)                          `  | ✓              |                       ||
| `User defined case class               `  | ✓              |                       ||

* `*` - `None` is converted into `null`
* `**` - `scala.Map[String, Any]`
