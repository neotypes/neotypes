---
layout: page
section: types
title: "Supported types"
position: 30
---

# Supported types

{:.table}
| Type                                       | Query result | Field of a case class | Query parameter |
| -----------------------------------------  |:------------:|:---------------------:|:-----------------|
| `scala.Boolean                           ` | ✓            | ✓                     | ✓ |
| `scala.Int                               ` | ✓            | ✓                     | ✓ |
| `scala.Long                              ` | ✓            | ✓                     | ✓ |
| `scala.Double                            ` | ✓            | ✓                     | ✓ |
| `scala.Float                             ` | ✓            | ✓                     | ✓ |
| `java.lang.String                        ` | ✓            | ✓                     | ✓ |
| `scala.Array[Byte]                       ` | ✓            | ✓                     | ✓ |
| `scala.Option[T] *                       ` | ✓            | ✓                     | ✓ `**` |
| `scala.collection._ *                    ` | ✓            | ✓                     | ✓ `***` |
| `refined.Refined[T, P] * ****            ` | ✓            | ✓                     | ✓ |
| `cats.data.Chain[T] * *****              ` | ✓            | ✓                     | ✓ |
| `cats.data.Const[T, U] * *****           ` | ✓            | ✓                     | ✓ |
| `cats.data.NonEmptyChain[T] * *****      ` | ✓            | ✓                     | ✓ |
| `cats.data.NonEmptyList[T] * *****       ` | ✓            | ✓                     | ✓ |
| `cats.data.NonEmptyMap[String, T] * *****` | ✓            | ✓                     | ✓ |
| `cats.data.NonEmptySet[T] * *****        ` | ✓            | ✓                     | ✓ |
| `cats.data.NonEmptyVector[T] * *****     ` | ✓            | ✓                     | ✓ |
| `java.time.Duration                      ` | ✓            | ✓                     | ✓ |
| `java.time.LocalDate                     ` | ✓            | ✓                     | ✓ |
| `java.time.LocalDateTime                 ` | ✓            | ✓                     | ✓ |
| `java.time.LocalTime                     ` | ✓            | ✓                     | ✓ |
| `java.time.Period                        ` | ✓            | ✓                     | ✓ |
| `java.time.OffsetDateTime                ` | ✓            | ✓                     | ✓ |
| `java.time.OffsetTime                    ` | ✓            | ✓                     | ✓ |
| `java.time.ZonedDateTime                 ` | ✓            | ✓                     | ✓ |
| `java.util.UUID                          ` | ✓            | ✓                     | ✓ |
| `org.neo4j.driver.Value                  ` | ✓            | ✓                     | ✓ |
| `org.neo4j.driver.types.IsoDuration      ` | ✓            | ✓                     | ✓ |
| `org.neo4j.driver.types.Point            ` | ✓            | ✓                     | ✓ |
| `org.neo4j.driver.types.Node             ` | ✓            | ✓                     | |
| `org.neo4j.driver.types.Relationship     ` | ✓            | ✓                     | |
| `neotypes.types.Path                     ` | ✓            | ✓                     | |
| `shapeless.HList                         ` | ✓            | ✓                     | |
| `Tuple (1-22)                            ` | ✓            | ✓                     | |
| `User defined case class                 ` | ✓            | ✓                     | |

> `*` Generic types are supported as long as the the `T` is also supported.<br>
> `**` `None` is converted into `null`.<br>
> `***` Map-like collections are supported only if their keys are of type `String`. Any other kind of tuples is not supported.<br>
> `****` Support is provided in the **neotypes-refined** _module_.<br>
> `*****` Support is provided in the **neotypes-cats-data** _module_.<br>

## Additional types

If you want to support your own types, then you would need to create your own _implicits_.

* For **fields of a case class**, you need an instance of `neotypes.mappers.ValueMapper[T]`. You can create a new instance:
  + From scratch by instantiating it `new ValueMapper[T] { ... }`.
  + Using the helper methods on the companion object like `fromCast` or `instance`.
  + Casting an already existing mapper using `map` or `flatMap`.

* For **query results**, you need an instance of `neotypes.mappers.ResultMapper[T]`. You can create a new instance:
  + From scratch by instantiating it `new ResultMapper[T] { ... }`.
  + From a **ValueMapper** `ResultMapper.fromValueMapper[T]`.
  + Using the helper methods on the companion object like `instance`.
  + Casting an already existing mapper using `map` or `flatMap`.

* For **query parameters**, you need an instance of `neotypes.mappers.ParameterMapper[T]`. You can create a new instance:
  + Casting an already existing mapper using `contramap`.

# Neo4j Id

Even if [**neo4j** does not recommend the use of the of the system id](https://neo4j.com/blog/dark-side-neo4j-worst-practices/), **neotypes** allows you to easily retrieve it.
You only need to ask for a property named `id` on your case classes.

Note: If your model also defines a custom `id` property, then your property will take precedence and we will return you that one instead of the system one.
If you also need the system one then you can ask for the `_id` property.
If you have a custom `_id` property, then yours will take precedence and the system id will be available in the `id` property.
If you define both `id` and `_id` as custom properties, then both will take precedence and the system id would be unreachable.

> Disclaimer: we also discourage you from using the system id; we only allow you to access it because the Java driver does.
