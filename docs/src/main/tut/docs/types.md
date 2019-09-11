---
layout: docs
title: "Supported types"
---

## Supported types

{:.table}
| Type                                       | Query result | Field of a case class | Query parameter |
| -----------------------------------------  |:------------:|:---------------------:|:-----------------|
| `scala.Boolean                           ` | ✓            |✓                      |✓|
| `scala.Int                               ` | ✓            |✓                      |✓|
| `scala.Long                              ` | ✓            |✓                      |✓|
| `scala.Double                            ` | ✓            |✓                      |✓|
| `scala.Float                             ` | ✓            |✓                      |✓|
| `java.lang.String                        ` | ✓            |✓                      |✓|
| `scala.Array[Byte]                       ` | ✓            |✓                      |✓|
| `scala.Option[T] *                       ` | ✓            |✓                      |✓ `**`|
| `scala.collection._ *                    ` |              |✓                      |✓ `***`|
| `refined.Refined[T, P] * ****            ` | ✓            |✓                      |✓|
| `cats.data.Chain[T] * *****              ` | ✓            |✓                      |✓|
| `cats.data.Const[T, U] * *****           ` | ✓            |✓                      |✓|
| `cats.data.NonEmptyChain[T] * *****      ` | ✓            |✓                      |✓|
| `cats.data.NonEmptyList[T] * *****       ` | ✓            |✓                      |✓|
| `cats.data.NonEmptyMap[String, T] * *****` | ✓            |✓                      |✓|
| `cats.data.NonEmptySet[T] * *****        ` | ✓            |✓                      |✓|
| `cats.data.NonEmptyVector[T] * *****     ` | ✓            |✓                      |✓|
| `java.time.Duration                      ` | ✓            |✓                      |✓|
| `java.time.LocalDate                     ` | ✓            |✓                      |✓|
| `java.time.LocalDateTime                 ` | ✓            |✓                      |✓|
| `java.time.LocalTime                     ` | ✓            |✓                      |✓|
| `java.time.Period                        ` | ✓            |✓                      |✓|
| `java.time.OffsetDateTime                ` | ✓            |✓                      |✓|
| `java.time.OffsetTime                    ` | ✓            |✓                      |✓|
| `java.time.ZonedDateTime                 ` | ✓            |✓                      |✓|
| `java.util.UUID                          ` | ✓            |✓                      |✓|
| `org.neo4j.driver.v1.Value               ` | ✓            |✓                      |✓|
| `org.neo4j.driver.v1.types.IsoDuration   ` | ✓            |✓                      |✓|
| `org.neo4j.driver.v1.types.Point         ` | ✓            |✓                      |✓|
| `org.neo4j.driver.v1.types.Node          ` | ✓            |✓                      ||
| `org.neo4j.driver.v1.types.Relationship  ` | ✓            |✓                      ||
| `shapeless.HList                         ` | ✓            |                       ||
| `neotypes.types.Path                     ` | ✓            |                       ||
| `Tuple (1-22)                            ` | ✓            |                       ||
| `User defined case class                 ` | ✓            |                       ||

> `*` Generic types are supported as long as the the `T` is also supported.<br>
> `**` `None` is converted into `null`.<br>
> `***` Map-like collections are supported only if their keys are of type `String`. Any other kind of tuples is not supported.
> `****` Support is provided in the **neotypes-refined** _module_.<br>
> `*****` Support is provided in the **neotypes-cats-data** _module_.<br>

## Additional types

If you want to support your own types, then you would need to create your own _implicits_.

* For **fields of a case class**, you need an instance of `neotypes.mappers.ValueMapper[T]`. You can create a new instace:
  + From scratch by instantiating it `new ValueMapper[T] { ... }`.
  + Using the helper methods on the companion object like `fromCast` or `instance`.
  + Casting an already existing mapper using `map` or `flatMap`.

* For **query results**, you need an instance of `neotypes.mappers.ResultMapper[T]`. You can create a new instace:
  + From scratch by instantiating it `new ResultMapper[T] { ... }`.
  + From a **ValueMapper** `ResultMapper.fromValueMapper[T]`.
  + Using the helper methods on the companion object like `instace`.
  + Casting an already existing mapper using `map` or `flatMap`.

* For **query parameters**, you need an instance of `neotypes.mappers.ParameterMapper[T]`. You can create a new instace:
  + Casting an already existing mapper using `contramap`.
