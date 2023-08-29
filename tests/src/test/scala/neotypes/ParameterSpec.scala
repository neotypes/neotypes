package neotypes

import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.syntax.all._
import org.neo4j.driver.Values
import org.neo4j.driver.types.{IsoDuration, Point}

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetTime, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.{ArraySeq, BitSet, SortedMap}

/** Base class for testing the mapping of inserted parameters. */
sealed trait BaseParameterSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  behavior of s"Inserting parameters using ${driverName}"

  it should "convert parameters" in executeAsFuture { driver =>
    val string: String = "test"
    val int: Int = 123
    val opt1: Option[Int] = None
    val opt2: Option[Int] = Some(5)
    val bytes: ArraySeq[Byte] = ArraySeq(0, 0, 1, 1)
    val list: List[Int] = List(5, 10)
    val vector: Vector[Int] = Vector(333)
    val set: Set[Int] = Set(100)
    val bitSet: BitSet = BitSet(200, 404)
    val localDate: LocalDate = LocalDate.now()
    val localDateTime: LocalDateTime = LocalDateTime.now()
    val localTime: LocalTime = LocalTime.now()
    val offsetTime: OffsetTime = OffsetTime.now()
    val zonedDateTime: ZonedDateTime = ZonedDateTime.now()
    val duration: Duration = Duration.ofSeconds(35)
    val period: Period = Period.of(0, 3, 0)
    val uuid: UUID = UUID.randomUUID()
    val isoDuration: IsoDuration = Values.isoDuration(1, 2, 3, 4).asIsoDuration()
    val point: Point = Values.point(7203, 3.5d, 5.3d).asPoint() // 7203 = Cartesian code.

    for {
      _ <- c"""CREATE (d: Data {
                 string: ${string},
                 int: ${int},
                 opt1: ${opt1},
                 opt2: ${opt2},
                 bytes: ${bytes},
                 list: ${list},
                 vector: ${vector},
                 set: ${set},
                 bitSet: ${bitSet},
                 localDate: ${localDate},
                 localDateTime: ${localDateTime},
                 localTime: ${localTime},
                 offsetTime: ${offsetTime},
                 zonedDateTime: ${zonedDateTime},
                 duration: ${duration},
                 period: ${period},
                 uuid: ${uuid},
                 isoDuration: ${isoDuration},
                 point: ${point}
               })""".execute.void(driver)
      node <- "MATCH (d: Data) RETURN d".query(ResultMapper.node).single(driver)
    } yield {
      node.getAs(key = "string", mapper = ResultMapper.string).value shouldBe string
      node.getAs(key = "int", mapper = ResultMapper.int).value shouldBe int
      node.getAs(key = "opt1", mapper = ResultMapper.option(ResultMapper.int)).value shouldBe opt1
      node.getAs(key = "opt2", mapper = ResultMapper.option(ResultMapper.int)).value shouldBe opt2
      node.getAs(key = "bytes", mapper = ResultMapper.bytes).value shouldBe bytes
      node.getAs(key = "list", mapper = ResultMapper.list(ResultMapper.int)).value shouldBe list
      node.getAs(key = "vector", mapper = ResultMapper.vector(ResultMapper.int)).value shouldBe vector
      node.getAs(key = "set", mapper = ResultMapper.set(ResultMapper.int)).value shouldBe set
      node.getAs(key = "bitSet", mapper = ResultMapper.collectAs(BitSet, ResultMapper.int)).value shouldBe bitSet
      node.getAs(key = "localDate", mapper = ResultMapper.javaLocalDate).value shouldBe localDate
      node.getAs(key = "localDateTime", mapper = ResultMapper.javaLocalDateTime).value shouldBe localDateTime
      node.getAs(key = "localTime", mapper = ResultMapper.javaLocalTime).value shouldBe localTime
      node.getAs(key = "offsetTime", mapper = ResultMapper.javaOffsetTime).value shouldBe offsetTime
      node.getAs(key = "zonedDateTime", mapper = ResultMapper.javaZonedDateTime).value shouldBe zonedDateTime
      node.getAs(key = "duration", mapper = ResultMapper.javaDuration).value shouldBe duration
      node.getAs(key = "period", mapper = ResultMapper.javaPeriod).value shouldBe period
      node.getAs(key = "uuid", mapper = ResultMapper.uuid).value shouldBe uuid
      node.getAs(key = "isoDuration", mapper = ResultMapper.neoDuration).value shouldBe isoDuration
      node.getAs(key = "point", mapper = ResultMapper.neoPoint).value shouldBe point
    }
  }

  it should "convert map-like parameters into a node" in executeAsFuture { driver =>
    val parameters = SortedMap("p1" -> 3, "p2" -> 5)

    for {
      _ <- c"CREATE (d: Data ${parameters})".execute.void(driver)
      node <- "MATCH (d: Data) RETURN d limit 1".query(ResultMapper.node).single(driver)
    } yield {
      node.getAs(key = "p1", mapper = ResultMapper.int).value shouldBe 3
      node.getAs(key = "p2", mapper = ResultMapper.int).value shouldBe 5
    }
  }
}

final class AsyncParameterSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseParameterSpec[F]

final class StreamParameterSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseParameterSpec[F]
