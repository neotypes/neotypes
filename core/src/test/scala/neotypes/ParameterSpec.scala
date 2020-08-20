package neotypes

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, OffsetTime, Period, ZonedDateTime}
import java.util.UUID

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.{Value, Values}
import org.neo4j.driver.types.{IsoDuration, Node, Point}
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.jdk.CollectionConverters._
import scala.concurrent.Future

/** Base class for testing the mapping of inserted parameters. */
final class ParameterSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationWordSpec(testkit) with Matchers {
  s"Inserting parameters for ${effectName}" should{
    "convert parameters" in executeAsFuture { s =>
      val name: String = "test"
      val born: Int = 123
      val age1: Option[Int] = None
      val age2: Option[Int] = Some(5)
      val lastName: Option[String] = None
      val middleName: Option[String] = Some("test2")
      val data: Array[Byte] = Array(0, 0, 1, 1)
      val list: List[Double] = List(5.0, 10.10)
      val set: Set[Long] = Set(100L)
      val sortedSet: SortedSet[Long] = SortedSet(200L, 404L)
      val vector: Vector[Long] = Vector(333L)
      val localDate: LocalDate = LocalDate.now()
      val localDateTime: LocalDateTime = LocalDateTime.now()
      val localTime: LocalTime = LocalTime.now()
      val offsetDateTime: OffsetDateTime = OffsetDateTime.now()
      val offsetTime: OffsetTime = OffsetTime.now()
      val zonedDateTime: ZonedDateTime = ZonedDateTime.now()
      val duration: Duration = Duration.ofSeconds(35)
      val period: Period = Period.of(0, 3, 0)
      val uuid: UUID = UUID.randomUUID()
      val isoDuration: IsoDuration = Values.isoDuration(1, 2, 3, 4).asIsoDuration()
      val point: Point = Values.point(7203, 3.5, 5.3).asPoint() // 7203 = Cartesian code.
    val value: Value = Values.value(0)

      for {
        _ <- c"""create (p: Person {
                 name: $name,
                 born: $born,
                 age1: $age1,
                 age2: $age2,
                 lastName: $lastName,
                 middleName: $middleName,
                 data: $data,
                 list: $list,
                 set: $set,
                 sortedSet: $sortedSet,
                 vector: $vector,
                 localDate: $localDate,
                 localDateTime: $localDateTime,
                 localTime: $localTime,
                 offsetDateTime: $offsetDateTime,
                 offsetTime: $offsetTime,
                 zonedDateTime: $zonedDateTime,
                 duration: $duration,
                 period: $period,
                 uuid: $uuid,
                 isoDuration: $isoDuration,
                 point: $point,
                 value: $value
               })""".query[Unit].execute(s)
        res <- "match (p:Person) return p limit 1".query[Node].single(s)
      } yield {
        res.get("name").asString shouldBe name
        res.get("born").asInt shouldBe born
        res.get("age1").isNull
        res.get("age2").asInt shouldBe age2.get
        res.get("lastName").isNull
        res.get("middleName").asString shouldBe middleName.get
        res.get("data").asByteArray.toList shouldBe data.toList
        res.get("list").asList.asScala.toList shouldBe list
        res.get("set").asList.asScala.toSet shouldBe set
        SortedSet(res.get("sortedSet").asList(_.asLong).asScala.toSeq : _*) shouldBe sortedSet
        res.get("vector").asList.asScala.toVector shouldBe vector
        res.get("localDate").asLocalDate shouldBe localDate
        res.get("localDateTime").asLocalDateTime shouldBe localDateTime
        res.get("localTime").asLocalTime shouldBe localTime
        res.get("offsetDateTime").asOffsetDateTime shouldBe offsetDateTime
        res.get("offsetTime").asOffsetTime shouldBe offsetTime
        res.get("zonedDateTime").asZonedDateTime shouldBe zonedDateTime
        Duration.ofSeconds(res.get("duration").asIsoDuration.seconds) shouldBe duration
        Period.ofMonths(res.get("period").asIsoDuration.months.toInt) shouldBe period
        UUID.fromString(res.get("uuid").asString) shouldBe uuid
        res.get("isoDuration").asIsoDuration shouldBe isoDuration
        res.get("point").asPoint shouldBe point
        res.get("value") shouldBe value
      }
    }

    "convert map-like parameters into a node" in executeAsFuture { s =>
      val parameters = SortedMap("p1" -> 3, "p2" -> 5)

      for {
        _ <- c"""create (p: Person ${parameters})""".query[Unit].execute(s)
        res <- "match (p:Person) return p limit 1".query[Node].single(s)
      } yield {
        res.get("p1").asInt shouldBe 3
        res.get("p2").asInt shouldBe 5
      }
    }
  }
}
