package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.Value
import org.scalatest.Ignore

import scala.concurrent.Future

@Ignore
class MapSessionSpec extends BaseIntegrationSpec[Future] {
  import MapSessionSpec._

  it should "create a map from a node" in execute { s =>
    "match (p:Person {name: 'Charlize Theron'}) return p"
      .query[(String, Value)]
      .map(s)
      .flatMap { map =>
        assert(map("name").asString == "Charlize Theron")
        assert(map("born").asInt == 1975)
      }
  }

  it should "create a case class from a node" in execute { s =>
    "match (p:Person {name: 'Charlize Theron'}) return p"
      .query[Person]
      .single(s)
      .flatMap { person =>
        assert(person.name == "Charlize Theron")
        assert(person.born == 1975)
        assert(person.extra == None)
      }
  }

  it should "create a map from a map projection" in execute { s =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
      .query[(String, Value)]
      .map(s)
      .flatMap { map =>
        assert(map("name").asString == "Charlize Theron")
        assert(map("born").asInt == 1975)
        assert(map("extra").asInt == 1)
      }
  }

  it should "create a case class from a map projection" in execute { s =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
      .query[Person]
      .single(s)
      .flatMap { person =>
        assert(person.name == "Charlize Theron")
        assert(person.born == 1975)
        assert(person.extra == Some(1))
      }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object MapSessionSpec {
  final case class Person(name: String, born: Int, extra: Option[Int])
}
