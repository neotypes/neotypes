---
layout: page
section: types
title: "Supported types"
position: 30
---

# Supported types

{:.table}
| Type                                     | Query result | Query parameter |
| ---------------------------------------- | :----------: | :-------------- |
| `scala.Boolean                         ` | ✓            | ✓ |
| `scala.Int                             ` | ✓            | ✓ |
| `scala.Long                            ` | ✓            | ✓ |
| `scala.Double                          ` | ✓            | ✓ |
| `scala.Float                           ` | ✓            | ✓ |
| `java.lang.String                      ` | ✓            | ✓ |
| `scala.Array[Byte]                     ` | ✓            | ✓ |
| `scala.Option[T] * **                  ` | ✓            | ✓ |
| `scala.collection._ * ***              ` | ✓            | ✓ |
| `refined.Refined[T, P] * +             ` | ✓            | ✓ |
| `cats.data.Chain[T] * ++               ` | ✓            | ✓ |
| `cats.data.Const[T, U] * ++            ` | ✓            | ✓ |
| `cats.data.NonEmptyChain[T] * ++       ` | ✓            | ✓ |
| `cats.data.NonEmptyList[T] * ++        ` | ✓            | ✓ |
| `cats.data.NonEmptyMap[K, V] * ++      ` | ✓            | ✓ |
| `cats.data.NonEmptySet[T] * ++         ` | ✓            | ✓ |
| `cats.data.NonEmptyVector[T] * ++      ` | ✓            | ✓ |
| `enumeratum.Enum +++                   ` | ✓            | ✓ |
| `enumeratum.values.ValueEnum +++       ` | ✓            | ✓ |
| `java.time.Duration                    ` | ✓            | ✓ |
| `java.time.LocalDate                   ` | ✓            | ✓ |
| `java.time.LocalDateTime               ` | ✓            | ✓ |
| `java.time.LocalTime                   ` | ✓            | ✓ |
| `java.time.Period                      ` | ✓            | ✓ |
| `java.time.OffsetDateTime              ` | ✓            | ✓ |
| `java.time.OffsetTime                  ` | ✓            | ✓ |
| `java.time.ZonedDateTime               ` | ✓            | ✓ |
| `java.util.UUID                        ` | ✓            | ✓ |
| `neotypes.DeferredQuery °°             ` |              | ✓ |
| `org.neo4j.driver.Value                ` | ✓            | ✓ |
| `org.neo4j.driver.types.IsoDuration    ` | ✓            | ✓ |
| `org.neo4j.driver.types.Point          ` | ✓            | ✓ |
| `org.neo4j.driver.types.Node           ` | ✓            | |
| `org.neo4j.driver.types.Relationship   ` | ✓            | |
| `neotypes.types.Path                   ` | ✓            | |
| `shapeless.HList °                     ` | ✓            | ✓ |
| `Tuple (1-22) °                        ` | ✓            | ✓ |
| `User defined case class °             ` | ✓            | ✓ |

> `*` Generic types are supported as long as the the `T` is also supported.<br>
> `**` `None` is converted into `null`.<br>
> `***` Map-like collections are supported only if their keys and values are also supported.<br>
> `+` Support is provided in the `neotypes-refined` _module_.<br>
> `++` Support is provided in the `neotypes-cats-data` _module_.<br>
> `+++` Support is provided in the `neotypes-enumeratum` _module_.<br>
> `°` Support for automatic derivation is provided in the `generic` _module_.<br>
> `°°` Nested queries are expanded at compile time.<br>

## Additional types

If you want to support your own types, then you would need to create your own _implicits_.

* For **query results**, you need an instance of `neotypes.mappers.ResultMapper[T]`.
You can create a new instance:
  + Combine existing instances using the provided combinators.
  + Or from scratch by instantiating it `ResultMapper.instance[T] { value => ... }` _(you rarely would need to resort to this)_.

* For **query parameters**, you need an instance of `neotypes.mappers.ParameterMapper[T]`.
You can create a new instance:
  + Transforming an already existing mapper using `contramap`.

* For **keys** in maps, you need an instance of `neotypes.mappers.KeyMapper[T]`
You can create a new instance:
  + Transforming an already existing mapper using `imap`.

## Combinators

`ResultMapper` is not designed to be used a typeclass but rather as an explicit value.<br>
You can appreciate that since the `query` method on `DeferredQueryBuilder`
expects a explicit `ResultMapper`, rather than an implicit one.

The idea is that instead of using implicit resolution to derive instances for your types,
you will use explicit combinators provided by the library to construct such instances.

```scala mdoc
import neotypes.generic.implicits._ // Provides automatic derivation of ResultMapper for any case class.
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.IncoercibleException
import ResultMapper._ // Put all combinators in scope.

// Mappers for primitive types.
int
string

// Mappers for the stdlib data types.
option(int)
either(boolean, bytes)
list(string)
collectAs(collection.immutable.BitSet, int)

// Mappers for tuples.
tuple(int, string)
tupleNamed("foo" -> int, "bar" -> string)

// Mappers for case classes.
final case class Data(foo: Int, bar: String)
product(int, string)(Data.apply)
productNamed("foo" -> int, "bar" -> string)(Data.apply)
fromFunction(Data.apply _)
fromFunctionNamed("foo", "bar")(Data.apply _)

// Mapper for sealed traits.
sealed trait Problem extends Product with Serializable
object Problem {
  final case class Error(msg: String) extends Problem
  object Error {
    implicit val resultMapper = ResultMapper.productDerive[Error]
  }

  final case class Warning(msg: String) extends Problem
  object Warning {
    implicit val resultMapper = ResultMapper.productDerive[Warning]
  }

  final case object Unknown extends Problem {
    implicit val resultMapper = ResultMapper.constant(this)
  }
}
val mapper = coproduct(strategy = CoproductDiscriminatorStrategy.RelationshipType)(
  "error" -> Problem.Error.resultMapper,
  "warning" -> Problem.Warning.resultMapper,
  "unknown" -> Problem.Unknown.resultMapper
)

// Apply custom validations.
final case class Id(int: Int)
object Id {
  def from(int: Int): Option[Id] =
    if (int >= 0) Some(Id(int)) else None
}
int.emap { i =>
  Id.from(i).toRight(
    left = IncoercibleException(s"${i} is not a valid ID because is negative")
  )
}
```

> You may check the `DriverSpec` for more examples.
> Also check the **Scaladoc** for `ResultMapper`

The rationale for preferring combinators over typeclasses is best explained by Fabio Labella on the **Dynosaur** motivation page: https://systemfw.org/dynosaur/#/motivation<br>
Having said that, if you still prefer the simplicity of automatic derivation,
you still can use `ResultMapper` in a semi-implicit way.

```scala
query.query(ResultMapper[T]) // For any type.
query.query(ResultMapper.productDerive[Foo]) // Only for case classes / ADTs.
```

> For more information about using combinators over typeclasses
> check Luis's Master's thesis: https://www.dropbox.com/s/tmx1p3y75uzg4ip/Memoria.pdf?dl=0
> The main document is in _Spanish_ but at the end is a small article in _English_.

# Neo4j Id

Even if [**Neo4j** does not recommend the use of the of the system id](https://neo4j.com/blog/dark-side-neo4j-worst-practices/), **neotypes** allows you to easily retrieve it.
You only need to ask for a property named `id` of type `String` on your case classes.

Note: If your model also defines a custom `id` property, then your property will take precedence and we will return you that one instead of the system one.
If you also need the system one then you can ask for the `_id` property.
If you have a custom `_id` property, then yours will take precedence and the system id will be available in the `id` property.
If you define both `id` and `_id` as custom properties, then both will take precedence and the system id would be unreachable.

> Disclaimer: we also discourage you from using the system id; we only allow you to access it because the **Java** driver does.
