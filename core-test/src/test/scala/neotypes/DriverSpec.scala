package neotypes

import neotypes.generic.implicits._
import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import neotypes.mappers.{KeyMapper, ResultMapper}
import neotypes.model.exceptions.{IncoercibleException, KeyMapperException}
import neotypes.model.types._

import org.neo4j.driver.summary.ResultSummary
import org.scalatest.{Inside, LoneElement, OptionValues}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import java.time.{LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import scala.collection.immutable.{ArraySeq, BitSet, SortedMap}

trait BaseDriverSpec[F[_]] extends CleaningIntegrationSpec[F] with Matchers with Inside with LoneElement with OptionValues { self: DriverProvider[F] with BaseEffectSpec[F] =>
  import BaseDriverSpec._
  import ResultMapper._

  it should "support querying primitive values" in executeAsFuture { driver =>
    // Value types.
    locally {
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
        str <- """RETURN "foo"""".query(string).single(driver)
        id <- """RETURN "d18e9810-87ad-444c-871e-7e41e0e4623c"""".query(uuid).single(driver)
        bytes <- c"RETURN ${expectedBytes}".query(bytes).single(driver)
        point <- c"RETURN point({x: 1, y: 3, z: 5})".query(neoPoint).single(driver)
        dur <- c"RETURN duration({seconds: 10})".query(javaDuration).single(driver)
      } yield {
        i shouldBe 3
        s shouldBe 3.toShort
        bit shouldBe 3.toByte
        l shouldBe 3L
        f shouldBe 3.0F
        d shouldBe 3.0D
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

    // Date types.
    locally {
      val localDate = "2023-01-02"
      val localTime = "13:30:00"
      val localDateTime = s"${localDate}T${localTime}"
      val timeZone = "[America/Bogota]"
      val zonedTime = s"${localTime}${timeZone}"
      val zonedDateTime = s"${localDateTime}${timeZone}"

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

    // Structural types.
    locally {
      for {
        n <- "CREATE (n: Node { data: 0 }) RETURN n".query(node).single(driver)
        r <- "CREATE ()-[r: RELATIONSHIP { data: 1 }]->() RETURN r".query(relationship).single(driver)
        p <- "CREATE p=(: Node { data: 3 })-[r: RELATIONSHIP { data: 5 }]->(: Node { data: 10 }) RETURN p".query(path).single(driver)
      } yield {
        assert(n.hasLabel("node"))
        n.properties should contain theSameElementsAs Map("data" -> Value.Integer(0))

        assert(r.hasType("relationship"))
        r.properties should contain theSameElementsAs Map("data" -> Value.Integer(1))

        inside(p.segments.loneElement) {
          case Path.Segment(start, relationship, end) =>
            assert(start.hasLabel("node"))
            start.properties should contain theSameElementsAs Map("data" -> Value.Integer(3))

            assert(relationship.hasType("relationship"))
            relationship.properties should contain theSameElementsAs Map("data" -> Value.Integer(5))

            assert(end.hasLabel("node"))
            end.properties should contain theSameElementsAs Map("data" -> Value.Integer(10))
        }
      }
    }
  }

  it should "execute a query and discard the output" in executeAsFuture { driver =>
    for {
      r <- "CREATE (: Node { id: 1})".execute.void(driver)
      id <- "MATCH (n: Node) RETURN n.id".query(int).single(driver)
    } yield {
      r shouldBe a [Unit]
      id shouldBe 1
    }
  }

  it should "execute a query and return its result summary" in executeAsFuture { driver =>
    val query = "CREATE (: Node { id: 1})"
    for {
      rs <- query.execute.resultSummary(driver)
      id <- "MATCH (n: Node) RETURN n.id".query(int).single(driver)
    } yield {
      rs shouldBe a [ResultSummary]
      rs.counters.nodesCreated shouldBe 1
      rs.query.text shouldBe query
      id shouldBe 1
    }
  }

  it should "support querying tuples of supported types" in executeAsFuture { driver =>
    val mapper = tuple(int, string)
    for {
      tuple <- """RETURN 3, "foo"""".query(mapper).single(driver)
    } yield {
      tuple shouldBe (3, "foo")
    }
  }

  it should "support querying nullable records as an option of a supported type" in executeAsFuture { driver =>
    val mapper = option(int)

    // Value.
    locally {
      for {
        opt <- "RETURN 3".query(mapper).single(driver)
      } yield {
        opt.value shouldBe 10
      }
    }

    // Null.
    locally {
      for {
        opt <- "RETURN null".query(mapper).single(driver)
      } yield {
        opt shouldBe None
      }
    }
  }

  it should "support querying any collection of supported types" in executeAsFuture { driver =>
    val multipleRecordQuery = "UNWIND [1, 2, 3] AS x RETURN x"
    val singleRecordQuery = "RETURN [1, 2, 3]"

    // Multiples records of single values (list).
    locally {
      val mapper = int
      for {
        nums <- multipleRecordQuery.query(mapper).list(driver)
      } yield {
        nums shouldBe List(1, 2, 3)
      }
    }

    // Multiples records of single values (collectAs).
    locally {
      val mapper = int
      for {
        nums <- multipleRecordQuery.query(mapper).collectAs(BitSet, driver)
      } yield {
        nums shouldBe BitSet(1, 2, 3)
      }
    }

    // Single record of multiple values (list).
    locally {
      val mapper = list(int)
      for {
        nums <- singleRecordQuery.query(mapper).single(driver)
      } yield {
        nums shouldBe List(1, 2, 3)
      }
    }

    // Single record of multiple values (collectAs).
    locally {
      val mapper = collectAs(int, BitSet)
      for {
        nums <- singleRecordQuery.query(mapper).single(driver)
      } yield {
        nums shouldBe BitSet(1, 2, 3)
      }
    }
  }

  it should "support querying user defined case classes whose fields are supported types" in executeAsFuture { driver =>
    val namedQuery = """RETURN "Luis" AS name, 25 AS age"""
    val unnamedQuery = """RETURN ["Luis", 25]"""
    val expectedUser = User(name = "Luis", age = 25)

    // Full manual definition.
    locally {
      val mapper = neoObject.emap { obj =>
        for {
          name <- obj.getAs(key = "name")(string) // Explicit field decoder.
          age <- obj.getAs[Int](key = "age") // Implicit field decoder.
        } yield User(name, age)
      }

      for {
        user <- namedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }

    // Using the product factory (named).
    locally {
      val mapper = productNamed(
        "name" -> string,
        "age" -> int
      )(User.apply)

      for {
        user <- namedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }

    // Using the product factory (unnamed).
    locally {
      val mapper = product(
        string,
        int
      )(User.apply)

      for {
        user <- unnamedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }

    // Using the fromFunction factory (named).
    locally {
      val mapper = fromFunctionNamed("name", "age")(User.apply)

      for {
        user <- namedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }

    // Using the fromFunction factory (unnamed).
    locally {
      val mapper = fromFunction(User.apply _)

      for {
        user <- unnamedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }

    // Using the implicit derivation mechanism.
    locally {
      val mapper = productDerive[User]

      for {
        user <- namedQuery.query(mapper).single(driver)
      } yield {
        user shouldBe expectedUser
      }
    }
  }

  it should "support querying user defined ADTs conformed of supported types" in executeAsFuture { driver =>
    val errorResultMapper = productDerive[Problem.Error]
    val warningResultMapper = productDerive[Problem.Warning]
    val unknownResultMapper = constant(Problem.Unknown)

    // Full manual definition.
    locally {
      val mapper = node.flatMap { node =>
        if (node.hasLabel("error")) errorResultMapper.widen[Problem]
        else if (node.hasLabel("warning")) warningResultMapper.widen[Problem]
        else if (node.hasLabel("unknown")) unknownResultMapper.widen[Problem]
        else failed[Problem](IncoercibleException(s"Unexpected labels: ${node.labels}"))
      }

      for {
        problem <- """CREATE (n: Node: Error { msg: "foo" }) RETURN n""".query(mapper).single(driver)
      } yield {
        problem shouldBe Problem.Error(msg = "foo")
      }
    }

    // Using the coproduct factory.
    locally {
      val mapper = coproduct[Problem](strategy = CoproductDiscriminatorStrategy.RelationshipType)(
        "error" -> errorResultMapper,
        "warning" -> warningResultMapper,
        "unknown" -> unknownResultMapper
      )
      for {
        problem <- """CREATE ()-[r: WARNING { msg: "bar" }]->() RETURN r""".query(mapper).single(driver)
      } yield {
        problem shouldBe Problem.Warning(msg = "bar")
      }
    }

    // Using the implicit derivation mechanism.
    locally {
      val mapper = coproductDerive[Problem]
      for {
        problem <- """RETURN { type: "Unknown", data: 10 }""".query(mapper).single(driver)
      } yield {
        problem shouldBe Problem.Unknown
      }
    }
  }

  it should "support renaming fields" in executeAsFuture { driver =>
    val mapper = productNamed(
      "personName" -> string,
      "personAge" -> int
    )(User.apply)

    for {
      r <- """RETURN "Balmung" AS personName, 135 AS personAge""".query(mapper).single(driver)
    } yield {
      r shouldBe User(name = "Balmung", age = 135)
    }
  }

  it should "support application of custom validations / transformations to fields" in {
    val idMapper = int.emap { i =>
      Id.from(i).toRight(
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
        record <- """RETURN 1 AS id, "foo" AS data""".query(recordMapper).single(driver)
      } yield {
        record shouldBe Record(id = Id(1), data = "foo")
      }
    }

    // Failed validation.
    recoverToExceptionIf[IncoercibleException] {
      executeAsFuture { driver =>
        """RETURN -1 AS id, "foo" AS data""".query(recordMapper).single(driver)
      }
    } map { ex =>
      ex.getMessage shouldBe "-1 is not a valid ID because is negative"
    }
  }

  it should "support combining multiple independent fields into a single value" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "dataStr" -> string,
      "dataInt" -> int
    ) {
      case (id, dataStr, dataInt) =>
        Combined(id, data = (dataStr, dataInt))
    }

    for {
      r <- """RETURN 1 AS id, "foo" AS dataStr, 5 AS dataInt""".query(mapper).single(driver)
    } yield {
      r shouldBe Combined(id = 1, data = ("foo", 5))
    }
  }

  it should "support splitting a single field into multiple values" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "data" -> tuple[String, Int]
    ) {
      case (id, (dataStr, dataInt)) =>
        Divided(id, dataStr, dataInt)
    }

    for {
      r <- """RETURN 1 AS id, ["foo", 5] AS data""".query(mapper).single(driver)
    } yield {
      r shouldBe Divided(id = 1, dataStr = "foo", dataInt = 5)
    }
  }

  it should "allow using default values for constructor arguments" in executeAsFuture { driver =>
    val mapper = productNamed(
      "id" -> int,
      "data" -> option[String]
    ) {
      case (id, opt) =>
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
        "b" -> string,
      )(Foo.apply),
      productNamed(
        "c" -> int,
        "d" -> string,
      )(Bar.apply)
    )(Nested.apply)

    for {
      r <- """RETURN 3 AS a, "foo" AS b, 5 AS c, "bar" AS d""".query(mapper).single(driver)
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
    query.query(int).withResultSummary.single(driver).map {
      case (i, rs) =>
        i shouldBe 3
        rs shouldBe a [ResultSummary]
        rs.query.text shouldBe query
    }
  }

  it should "support querying objects as custom maps" in executeAsFuture { driver =>
    val customKeyMapper =
      KeyMapper.StringKeyMapper.imap[CustomKey](_.name) { name =>
        CustomKey.from(name).toRight(
          left = KeyMapperException(
            key = name,
            cause = IncoercibleException(
              message = s"${name} is not a valid CustomKey"
            )
          )
        )
      }

    val mapper = neoMap(
      mapper = int,
      keyMapper = customKeyMapper
    ) (
      mapFactory = SortedMap
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
}

object BaseDriverSpec {
  final case class User(name: String, age: Int)

  sealed trait Problem
  object Problem {
    final case class Error(msg: String) extends Problem
    final case class Warning(msg: String) extends Problem
    case object Unknown extends Problem
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
      case _ => None
    }

    implicit val ordering: Ordering[CustomKey] =
      Ordering.by(_.name)
  }
}

final class AsyncDriverSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider(testkit) with BaseDriverSpec[F] {
  behavior of s"Driver[${effectName}]"
}

final class StreamingDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider(testkit) with BaseDriverSpec[F] {
  behavior of s"StreamingDriver[${streamName}, ${effectName}]"

  it should "support stream the records" in {
    executeAsFutureList { driver =>
      "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(driver)
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }
}
