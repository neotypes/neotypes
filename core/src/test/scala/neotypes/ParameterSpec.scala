package neotypes

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.UUID

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.{Value, Values}
import org.neo4j.driver.v1.types.{IsoDuration, Node, Point}

import scala.collection.immutable.SortedSet
import scala.jdk.CollectionConverters._
import scala.concurrent.Future

class ParameterSpec extends BaseIntegrationSpec[Future] {
  it should "convert parameters" in execute { s =>
    val name: String = "test"
    val born: Int = 123
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
      assert(res.get("name").asString == name)
      assert(res.get("born").asInt == born)
      assert(res.get("lastName").isNull)
      assert(res.get("middleName").asString == middleName.get)
      assert(res.get("data").asByteArray.toList == data.toList)
      assert(res.get("list").asList.asScala.toList == list)
      assert(res.get("set").asList.asScala.toSet == set)
      assert(SortedSet.from(res.get("sortedSet").asList(_.asLong).asScala) == sortedSet)
      assert(res.get("vector").asList.asScala.toVector == vector)
      assert(res.get("localDate").asLocalDate == localDate)
      assert(res.get("localDateTime").asLocalDateTime == localDateTime)
      assert(res.get("localTime").asLocalTime == localTime)
      assert(res.get("offsetDateTime").asOffsetDateTime == offsetDateTime)
      assert(res.get("offsetTime").asOffsetTime == offsetTime)
      assert(res.get("zonedDateTime").asZonedDateTime == zonedDateTime)
      assert(Duration.ofSeconds(res.get("duration").asIsoDuration.seconds) == duration)
      assert(Period.ofMonths(res.get("period").asIsoDuration.months.toInt) == period)
      assert(UUID.fromString(res.get("uuid").asString) == uuid)
      assert(res.get("isoDuration").asIsoDuration == isoDuration)
      assert(res.get("point").asPoint == point)
      assert(res.get("value") == value)
    }
  }

  override val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
