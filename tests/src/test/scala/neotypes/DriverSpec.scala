package neotypes

import neotypes.generic.implicits._
import neotypes.internal.syntax.async._
import neotypes.mappers.{KeyMapper, ResultMapper}
import neotypes.model.exceptions.{IncoercibleException, KeyMapperException, MissingRecordException}
import neotypes.model.types._
import neotypes.syntax.all._
import org.neo4j.driver.summary.ResultSummary

import java.util.UUID
import java.time.{
  LocalDate => JDate,
  LocalDateTime => JDateTime,
  LocalTime => JTime,
  OffsetTime => JZTime,
  ZonedDateTime => JZDateTime
}
import scala.collection.immutable.{ArraySeq, BitSet, SortedMap}

/** Base class for testing the basic behaviour of running queries. */
sealed trait BaseDriverSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  import BaseDriverSpec._
  import ResultMapper._

  behavior of driverName

  it should "support querying primitive values" in executeAsFuture { driver =>
    val expectedBytes = ArraySeq(1.byteValue, 3.byteValue, 5.byteValue)

    for {
      i <- "RETURN 3".query(int).single(driver)
      s <- "RETURN 3".query(short).single(driver)
      bit <- "RETURN 3".query(byte).single(driver)
      l <- "RETURN 3".query(long).single(driver)
      f <- "RETURN 3.0".query(float).single(driver)
      d <- "RETURN 3.0".query(double).single(driver)
      bi <- "RETURN 3".query(bigInt).single(driver)
      bd <- "RETURN 3.0".query(bigDecimal).single(driver)
      b <- "RETURN true".query(boolean).single(driver)
      str <- "RETURN 'foo'".query(string).single(driver)
      id <- "RETURN 'd18e9810-87ad-444c-871e-7e41e0e4623c'".query(uuid).single(driver)
      bytes <- c"RETURN ${expectedBytes}".query(bytes).single(driver)
      point <- "RETURN point({x: 1, y: 3, z: 5})".query(neoPoint).single(driver)
      dur <- "RETURN duration({seconds: 10})".query(javaDuration).single(driver)
    } yield {
      i shouldBe 3
      s shouldBe 3.toShort
      bit shouldBe 3.toByte
      l shouldBe 3L
      f shouldBe 3.0f
      d shouldBe 3.0d
      bi shouldBe BigInt("3")
      bd shouldBe BigDecimal("3.0")
      b shouldBe true
      str shouldBe "foo"
      id shouldBe UUID.fromString("d18e9810-87ad-444c-871e-7e41e0e4623c")
      bytes shouldBe expectedBytes

      point.x.toInt shouldBe 1
      point.y.toInt shouldBe 3
      point.z.toInt shouldBe 5

      dur.toSeconds shouldBe 10L
    }
  }

  it should "support querying date values" in executeAsFuture { driver =>
    val localDate = "2023-01-02"
    val localTime = "13:30:00"
    val localDateTime = s"${localDate}T${localTime}"
    val zonedTime = s"${localTime}+00:00"
    val zonedDateTime = s"${localDateTime}Z"

    for {
      ld <- c"RETURN date(${localDate})".query(javaLocalDate).single(driver)
      lt <- c"RETURN time(${localTime})".query(javaLocalTime).single(driver)
      ldt <- c"RETURN datetime(${localDateTime})".query(javaLocalDateTime).single(driver)
      zt <- c"RETURN time(${zonedTime})".query(javaOffsetTime).single(driver)
      zdt <- c"RETURN datetime(${zonedDateTime})".query(javaZonedDateTime).single(driver)
    } yield {
      ld shouldBe JDate.parse(localDate)
      lt shouldBe JTime.parse(localTime)
      ldt shouldBe JDateTime.parse(localDateTime)
      zt shouldBe JZTime.parse(zonedTime)
      zdt shouldBe JZDateTime.parse(zonedDateTime)
    }
  }

  it should "support querying structural types" in executeAsFuture { driver =>
    for {
      n <- "CREATE (n: Node { data: 0 }) RETURN n"
        .query(node)
        .single(driver)
      r <- "CREATE ()-[r: RELATIONSHIP { data: 1 }]->() RETURN r"
        .query(relationship)
        .single(driver)
      p <- "CREATE p=(: Node { data: 3 })-[: RELATIONSHIP { data: 5 }]->(: Node { data: 10 }) RETURN p"
        .query(path)
        .single(driver)
    } yield {
      assert(n.hasLabel("node"))
      n.properties should contain theSameElementsAs Map("data" -> Value.Integer(0))

      assert(r.hasType("relationship"))
      r.properties should contain theSameElementsAs Map("data" -> Value.Integer(1))

      inside(p.segments.loneElement) { case Path.Segment(start, relationship, end) =>
        assert(start.hasLabel("node"))
        start.properties should contain theSameElementsAs Map("data" -> Value.Integer(3))

        assert(relationship.hasType("relationship"))
        relationship.properties should contain theSameElementsAs Map("data" -> Value.Integer(5))

        assert(end.hasLabel("node"))
        end.properties should contain theSameElementsAs Map("data" -> Value.Integer(10))
      }
    }
  }

  it should "execute a query and discard the output" in executeAsFuture { driver =>
    for {
      r <- "CREATE (: Node { id: 1})".execute.void(driver)
      id <- "MATCH (n: Node) RETURN n.id".query(int).single(driver)
    } yield {
      r shouldBe a[Unit]
      id shouldBe 1
    }
  }

  it should "execute a query and return its result summary" in executeAsFuture { driver =>
    val query = "CREATE (: Node { id: 1})"

    for {
      rs <- query.execute.resultSummary(driver)
      id <- "MATCH (n: Node) RETURN n.id".query(int).single(driver)
    } yield {
      rs shouldBe a[ResultSummary]
      rs.counters.nodesCreated shouldBe 1
      rs.query.text shouldBe query
      id shouldBe 1
    }
  }

  it should "support querying unnamed tuples of supported types" in executeAsFuture { driver =>
    val expectedTuple = (25, "Luis")
    val mapper = tuple(int, string)

    for {
      tuple <- "RETURN 25, 'Luis'".query(mapper).single(driver)
    } yield {
      tuple shouldBe expectedTuple
    }
  }

  it should "support querying named tuples of supported types" in executeAsFuture { driver =>
    val expectedTuple = (25, "Luis")
    val mapper = tupleNamed(
      "age" -> int,
      "name" -> string
    )

    for {
      tuple <- "RETURN 25 AS age, 'Luis' AS name".query(mapper).single(driver)
    } yield {
      tuple shouldBe expectedTuple
    }
  }

  it should "support querying nullable records as an option of a supported type" in executeAsFuture { driver =>
    val mapper = option(int)

    for {
      r1 <- "RETURN 10".query(mapper).single(driver)
      r2 <- "RETURN null".query(mapper).single(driver)
    } yield {
      r1.value shouldBe 10
      r2 shouldBe None
    }
  }

  it should "support querying either values" in executeAsFuture { driver =>
    val mapper = either(int, string)

    for {
      left <- "RETURN 5".query(mapper).single(driver)
      right <- "RETURN 'Luis'".query(mapper).single(driver)
    } yield {
      left shouldBe Left(5)
      right shouldBe Right("Luis")
    }
  }

  it should "support querying multiple records as a list" in executeAsFuture { driver =>
    for {
      nums <- "UNWIND [1, 2, 3] AS x RETURN x".query(int).list(driver)
    } yield {
      nums shouldBe List(1, 2, 3)
    }
  }

  it should "support querying multiple records as any collection" in executeAsFuture { driver =>
    for {
      nums <- "UNWIND [1, 2, 3] AS x RETURN x".query(int).collectAs(BitSet, driver)
    } yield {
      nums shouldBe BitSet(1, 2, 3)
    }
  }

  it should "support querying a Neo4j list as Scala list" in executeAsFuture { driver =>
    val mapper = list(int)

    for {
      nums <- "RETURN [1, 2, 3]".query(mapper).single(driver)
    } yield {
      nums shouldBe List(1, 2, 3)
    }
  }

  it should "support querying a Neo4j list as any Scala collection" in executeAsFuture { driver =>
    val mapper = collectAs(BitSet, int)

    for {
      nums <- "RETURN [1, 2, 3]".query(mapper).single(driver)
    } yield {
      nums shouldBe BitSet(1, 2, 3)
    }
  }

  it should "support querying multiple key-value records as a map" in executeAsFuture { driver =>
    val mapper = tuple(int, string)

    for {
      nums <- "UNWIND [[1, 'a'], [2, 'b'], [3, 'c']] AS x RETURN x".query(mapper).map(driver)
    } yield {
      nums shouldBe Map(
        1 -> "a",
        2 -> "b",
        3 -> "c"
      )
    }
  }

  it should "support querying multiple key-value records as a custom map" in executeAsFuture { driver =>
    val mapper = tuple(int, string)

    for {
      nums <- "UNWIND [[1, 'a'], [2, 'b'], [3, 'c']] AS x RETURN x".query(mapper).collectAs(SortedMap, driver)
    } yield {
      nums shouldBe SortedMap(
        1 -> "a",
        2 -> "b",
        3 -> "c"
      )
    }
  }

  it should "support querying a Neo4j key-value list as Scala map" in executeAsFuture { driver =>
    val mapper = map(int, string)

    for {
      nums <- "RETURN [[1, 'a'], [2, 'b'], [3, 'c']]".query(mapper).single(driver)
    } yield {
      nums shouldBe Map(
        1 -> "a",
        2 -> "b",
        3 -> "c"
      )
    }
  }

  it should "support querying a Neo4j key-value list as Scala custom map" in executeAsFuture { driver =>
    val mapper = collectAs(SortedMap.sortedMapFactory[Int, String], tuple(int, string))

    for {
      nums <- "RETURN [[1, 'a'], [2, 'b'], [3, 'c']]".query(mapper).single(driver)
    } yield {
      nums shouldBe SortedMap(
        1 -> "a",
        2 -> "b",
        3 -> "c"
      )
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (manual mapper)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = neoObject.emap { obj =>
      for {
        name <- obj.getAs(key = "name", mapper = string)
        age <- obj.getAs(key = "age", mapper = int)
      } yield User(name, age)
    }

    for {
      user <- "RETURN 'Luis' AS name, 25 AS age".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (product named)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = productNamed(
      "name" -> string,
      "age" -> int
    )(User.apply)

    for {
      user <- "RETURN 'Luis' AS name, 25 AS age".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (product unnamed)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = product(
      string,
      int
    )(User.apply)

    for {
      user <- "RETURN 'Luis', 25".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (fromFunction named)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = fromFunctionNamed("name", "age")(User.apply)

    for {
      user <- "RETURN 'Luis' AS name, 25 AS age".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (fromFunction unnamed)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = fromFunction(User.apply _)

    for {
      user <- "RETURN 'Luis', 25".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined case classes whose fields are supported types (derivation)"
  ) in executeAsFuture { driver =>
    val expectedUser = User(name = "Luis", age = 25)
    val mapper = productDerive[User]

    for {
      user <- "RETURN 'Luis' AS name, 25 AS age".query(mapper).single(driver)
    } yield {
      user shouldBe expectedUser
    }
  }

  it should (
    "support querying user defined ADTs conformed of supported types (manual mapper)"
  ) in executeAsFuture { driver =>
    val mapper = node.flatMap { node =>
      if (node.hasLabel("error")) Problem.Error.resultMapper.widen[Problem]
      else if (node.hasLabel("warning")) Problem.Warning.resultMapper.widen[Problem]
      else if (node.hasLabel("unknown")) Problem.Unknown.resultMapper.widen[Problem]
      else failed[Problem](IncoercibleException(s"Unexpected labels: ${node.labels}"))
    }

    for {
      problem <- "CREATE (n: Node: Error { msg: 'foo' }) RETURN n".query(mapper).single(driver)
    } yield {
      problem shouldBe Problem.Error(msg = "foo")
    }
  }

  it should (
    "support querying user defined ADTs conformed of supported types (coproduct factory)"
  ) in executeAsFuture { driver =>
    val mapper = coproduct(strategy = CoproductDiscriminatorStrategy.RelationshipType)(
      "error" -> Problem.Error.resultMapper,
      "warning" -> Problem.Warning.resultMapper,
      "unknown" -> Problem.Unknown.resultMapper
    )

    for {
      problem <- "CREATE ()-[r: WARNING { msg: 'bar' }]->() RETURN r".query(mapper).single(driver)
    } yield {
      problem shouldBe Problem.Warning(msg = "bar")
    }
  }

  it should (
    "support querying user defined ADTs conformed of supported types (derivation)"
  ) in executeAsFuture { driver =>
    val mapper = coproductDerive[Problem]

    for {
      problem <- "RETURN { type: 'Unknown', data: 10 }".query(mapper).single(driver)
    } yield {
      problem shouldBe Problem.Unknown
    }
  }

  it should "support renaming fields" in executeAsFuture { driver =>
    val mapper = productNamed(
      "personName" -> string,
      "personAge" -> int
    )(User.apply)

    for {
      r <- "RETURN 'Luis' AS personName, 25 AS personAge".query(mapper).single(driver)
    } yield {
      r shouldBe User(name = "Luis", age = 25)
    }
  }

  it should "support application of custom validations / transformations to fields" in {
    val idMapper = int.emap { i =>
      Id
        .from(i)
        .toRight(
          left = IncoercibleException(s"${i} is not a valid ID because is negative")
        )
    }

    val recordMapper = productNamed(
      "id" -> idMapper,
      "data" -> string
    )(Record.apply)

    // Successful validation.
    executeAsFuture { driver =>
      for {
        record <- "RETURN 1 AS id, 'foo' AS data".query(recordMapper).single(driver)
      } yield {
        record shouldBe Record(id = Id(1), data = "foo")
      }
    }

    // Failed validation.
    recoverToExceptionIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN -1 AS id, 'foo' AS data".query(recordMapper).single(driver)
      }
    } map { ex =>
      ex.getMessage shouldBe "-1 is not a valid ID because is negative for field 'id'"
    }
  }

  it should "support combining multiple independent fields into a single value" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "dataStr" -> string,
      "dataInt" -> int
    ) { case (id, dataStr, dataInt) =>
      Combined(id, data = (dataStr, dataInt))
    }

    for {
      r <- "RETURN 1 AS id, 'foo' AS dataStr, 5 AS dataInt".query(mapper).single(driver)
    } yield {
      r shouldBe Combined(id = 1, data = ("foo", 5))
    }
  }

  it should "support splitting a single field into multiple values" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "data" -> tuple(string, int)
    ) { case (id, (dataStr, dataInt)) =>
      Divided(id, dataStr, dataInt)
    }

    for {
      r <- "RETURN 1 AS id, ['foo', 5] AS data".query(mapper).single(driver)
    } yield {
      r shouldBe Divided(id = 1, dataStr = "foo", dataInt = 5)
    }
  }

  it should "allow using default values for constructor arguments" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "data" -> option[String]
    ) { case (id, opt) =>
      Optional(id, opt1 = opt)
    }

    for {
      r <- "RETURN 1 AS id".query(mapper).single(driver)
    } yield {
      r shouldBe Optional(id = 1, opt1 = None, opt2 = 0)
    }
  }

  it should "support nest results" in executeAsFuture { driver =>
    val mapper = combine(
      productNamed(
        "a" -> int,
        "b" -> string
      )(Foo.apply),
      productNamed(
        "c" -> int,
        "d" -> string
      )(Bar.apply)
    )(Nested.apply)

    for {
      r <- "RETURN 3 AS a, 'foo' AS b, 5 AS c, 'bar' AS d".query(mapper).single(driver)
    } yield {
      r shouldBe Nested(
        foo = Foo(
          a = 3,
          b = "foo"
        ),
        bar = Bar(
          c = 5,
          d = "bar"
        )
      )
    }
  }

  it should "support querying and getting the result summary at the same time" in executeAsFuture { driver =>
    val query = "RETURN 3"

    query.query(int).withResultSummary.single(driver).map { case (i, rs) =>
      i shouldBe 3
      rs shouldBe a[ResultSummary]
      rs.query.text shouldBe query
    }
  }

  it should "support querying objects as maps" in executeAsFuture { driver =>
    val mapper = neoMap(valueMapper = int)

    for {
      map <- "RETURN { foo: 3, bar: 5 }".query(mapper).single(driver)
    } yield {
      map shouldBe Map(
        "foo" -> 3,
        "bar" -> 5
      )
    }
  }

  it should "support querying objects as custom maps" in executeAsFuture { driver =>
    val mapper = collectAsNeoMap(
      mapFactory = SortedMap.sortedMapFactory[CustomKey, Int],
      keyMapper = CustomKey.keyMapper,
      valueMapper = int
    )

    for {
      map <- "RETURN { foo: 3, bar: 5 }".query(mapper).single(driver)
    } yield {
      map shouldBe SortedMap(
        CustomKey.Foo -> 3,
        CustomKey.Bar -> 5
      )
    }
  }

  it should "support querying objects as lists" in executeAsFuture { driver =>
    val mapper = list(int)

    for {
      r <- "RETURN { foo: 1, bar: 3, baz: 5 }".query(mapper).single(driver)
    } yield {
      r should contain theSameElementsAs List(1, 3, 5)
    }
  }

  it should "support querying heterogeneous data as a list of values" in executeAsFuture { driver =>
    val mapper = values

    for {
      r <- "RETURN [1, 'balmung', true]".query(mapper).single(driver)
    } yield {
      inside(r) { case Value.Integer(i) :: Value.Str(s) :: Value.Bool(b) :: Nil =>
        i shouldBe 1
        s shouldBe "balmung"
        b shouldBe true
      }
    }
  }

  it should "support querying heterogeneous data as a map of values" in executeAsFuture { driver =>
    val mapper = neoObject

    for {
      r <- "RETURN { foo: 1, bar: 'balmung', baz: true }".query(mapper).single(driver)
    } yield {
      inside(r.get(key = "foo")) { case Value.Integer(i) =>
        i shouldBe 1
      }

      inside(r.get(key = "bar")) { case Value.Str(s) =>
        s shouldBe "balmung"
      }

      inside(r.get(key = "baz")) { case Value.Bool(b) =>
        b shouldBe true
      }
    }
  }

  it should "support querying nested lists" in executeAsFuture { driver =>
    val mapper = list(list(int))

    for {
      r <- "RETURN [[1, 2], [3, 4]]".query(mapper).single(driver)
    } yield {
      r shouldBe List(
        List(1, 2),
        List(3, 4)
      )
    }
  }

  it should "support querying nested maps" in executeAsFuture { driver =>
    val mapper = neoMap(valueMapper = neoMap(valueMapper = int))

    for {
      r <- "RETURN { foo: { bar: 1, baz: 3 }, quax: { haux: 5 }, faux: { } }".query(mapper).single(driver)
    } yield {
      r shouldBe Map(
        "foo" -> Map("bar" -> 1, "baz" -> 3),
        "quax" -> Map("haux" -> 5),
        "faux" -> Map.empty
      )
    }
  }

  it should "support treating missing named fields as nulls" in executeAsFuture { driver =>
    val expectedResult = MissingFields(a = 1, b = None)
    val mapper = productNamed(
      "a" -> int,
      "b" -> option(string)
    )(MissingFields.apply)

    for {
      result <- "RETURN 1 AS a".query(mapper).single(driver)
    } yield {
      result shouldBe expectedResult
    }
  }

  it should "support treating missing positional fields as nulls" in executeAsFuture { driver =>
    val expectedResult = MissingFields(a = 1, b = None)
    val mapper = product(
      int,
      option(string)
    )(MissingFields.apply)

    for {
      result <- "RETURN 1".query(mapper).single(driver)
    } yield {
      result shouldBe expectedResult
    }
  }

  it should "support catch exceptions during a query" in {
    recoverToSucceededIf[MissingRecordException] {
      executeAsFuture { driver =>
        "bad query".execute.void(driver)
      }
    }
  }

  it should "support rollback on cancellation" in executeAsFuture { driver =>
    for {
      _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(driver)
      _ <- cancel("CREATE (p: PERSON { name: 'Dmitry' })".execute.void(driver))
      people <- "MATCH (p: PERSON) RETURN p.name".query(string).set(driver)
    } yield {
      people.loneElement shouldBe "Luis"
    }
  }

  it should "construct an IncoercibleException message with a field name and value" in {
    val mapper = field(key = "name", mapper = int)

    recoverToExceptionIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN { name: 'Charlize Theron' }".query(mapper).single(driver)
      }
    } map { ex =>
      ex.getMessage shouldBe "Couldn't decode Str(Charlize Theron) into a int for field 'name'"
    }
  }
}

object BaseDriverSpec {
  final case class User(name: String, age: Int)

  sealed trait Problem extends Product with Serializable
  object Problem {
    final case class Error(msg: String) extends Problem
    object Error {
      implicit val resultMapper: ResultMapper[Error] =
        ResultMapper.productDerive
    }

    final case class Warning(msg: String) extends Problem
    object Warning {
      implicit val resultMapper: ResultMapper[Warning] =
        ResultMapper.productDerive
    }

    final case object Unknown extends Problem {
      implicit val resultMapper: ResultMapper[Unknown.type] =
        ResultMapper.constant(this)
    }
  }

  final case class Id(int: Int)
  object Id {
    def from(int: Int): Option[Id] =
      if (int >= 0) Some(Id(int)) else None
  }
  final case class Record(id: Id, data: String)

  final case class Combined(id: Int, data: (String, Int))

  final case class Divided(id: Int, dataStr: String, dataInt: Int)

  final case class Optional(id: Int, opt1: Option[String], opt2: Int = 0)

  final case class Nested(foo: Foo, bar: Bar)
  final case class Foo(a: Int, b: String)
  final case class Bar(c: Int, d: String)

  sealed trait CustomKey extends Product with Serializable {
    def name: String
  }
  object CustomKey {
    final case object Foo extends CustomKey {
      override final val name: String = "FOO"
    }
    final case object Bar extends CustomKey {
      override final val name: String = "Bar"
    }

    def from(name: String): Option[CustomKey] = name.toUpperCase match {
      case "FOO" => Some(Foo)
      case "BAR" => Some(Bar)
      case _     => None
    }

    implicit val ordering: Ordering[CustomKey] =
      Ordering.by(_.name)

    implicit val keyMapper: KeyMapper[CustomKey] =
      KeyMapper.string.imap[CustomKey](_.name) { name =>
        CustomKey
          .from(name)
          .toRight(
            left = KeyMapperException(
              key = name,
              cause = IncoercibleException(
                message = s"${name} is not a valid CustomKey"
              )
            )
          )
      }
  }

  final case class MissingFields(a: Int, b: Option[String])
}

final class AsyncDriverSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseDriverSpec[F]

final class StreamDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseDriverSpec[F] {
  it should "support stream the records" in {
    executeAsFutureList { driver =>
      "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(driver)
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }

  it should "support stream the records and getting the result summary at the same time" in {
    val query = "UNWIND [1, 2, 3] AS x RETURN x"

    executeAsFutureList { driver =>
      query.query(ResultMapper.int).withResultSummary.stream(driver)
    } map { r =>
      inside(r.partitionMap(identity)) { case (ints, rs :: Nil) =>
        ints shouldBe List(1, 2, 3)
        rs shouldBe a[ResultSummary]
        rs.query.text shouldBe query
      }
    }
  }
}
