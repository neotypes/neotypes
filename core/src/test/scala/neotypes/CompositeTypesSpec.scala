package neotypes

import neotypes.exceptions.IncoercibleException
import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.Value
import scala.jdk.CollectionConverters._
import org.scalatest.matchers.should.Matchers._

/** Base class for testing queries which produce complex types. */
final class CompositeTypesSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  import CompositeTypesSpec._

  s"Extracting complex types using: ${effectName}" should {
    "create a map from a node" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p"
        .query[Map[String, Value]]
        .single(d)
        .map { map =>
          map("name").asString shouldBe "Charlize Theron"
          map("born").asInt shouldBe 1975
        }
    }
    "create a case class from a node" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p"
        .query[Person]
        .single(d)
        .map { person =>
          person.name shouldBe "Charlize Theron"
          person.born shouldBe 1975
          person.extra shouldBe None
        }
    }
    "create a map from a map projection" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
        .query[Map[String, Value]]
        .single(d)
        .map { map =>
          map("name").asString shouldBe "Charlize Theron"
          map("born").asInt shouldBe 1975
          map("extra").asInt shouldBe 1
        }
    }
    "create a map from a map projection with a nested map" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .name, map: { foo: 3, bar: 5 } }"
        .query[Map[String, Value]]
        .single(d)
        .map { map =>
          map("name").asString shouldBe "Charlize Theron"
          val nestedMap = map("map").asMap[Value]((v: Value) => v).asScala
          nestedMap("foo").asInt shouldBe 3
          nestedMap("bar").asInt shouldBe 5
        }
    }
    "create a case class from a map projection" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
        .query[Person]
        .single(d)
        .map { person =>
          person.name shouldBe "Charlize Theron"
          person.born shouldBe 1975
          person.extra shouldBe Some(1)
        }
    }
    "create a case class from a map projection with a nested map" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .name, map: { foo: 3, bar: 5 } }"
        .query[PersonWithNestedMap]
        .single(d)
        .map { person =>
          person.name shouldBe "Charlize Theron"
          person.map("foo") shouldBe 3
          person.map("bar") shouldBe 5
        }
    }
    "create a nested case class from a map projection with a nested map" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .name, fooBar: { foo: 3, bar: '5' } }"
        .query[PersonWithNestedClass]
        .single(d)
        .map { person =>
          person.name shouldBe "Charlize Theron"
          person.fooBar.foo shouldBe 3
          person.fooBar.bar shouldBe "5"
        }
    }
    "create a List from a Neo4j LIST" in executeAsFuture { d =>
      "return [1, 2]"
        .query[List[Int]]
        .single(d)
        .map { _ shouldBe List(1, 2)
        }
    }
    "create a nested List from a nested Neo4j LIST" in executeAsFuture { d =>
      "return [['a', 'b'], ['c', 'd']]"
        .query[List[List[String]]]
        .single(d)
        .map { _ shouldBe List(List("a", "b"), List("c", "d"))
        }
    }
    "create a List from a map projection" in executeAsFuture { d =>
      "match (p:Person {name: 'Charlize Theron'}) return p { .name, .born, extra: 1 }"
        .query[List[Value]]
        .single(d)
        .map { list =>
          list.length shouldBe 3
          list(0).asString shouldBe "Charlize Theron"
          list(1).asInt shouldBe 1975
          list(2).asInt shouldBe 1
        }
    }
    "construct an IncoercibleException message with a field name and value" in {
      recoverToExceptionIf[IncoercibleException] {
        executeAsFuture { d =>
          "match (p:Person {name: 'Charlize Theron'}) return p"
            .query[PersonIntName]
            .single(d)
        }
      } map { ex =>
        ex.getMessage  shouldBe "Cannot coerce STRING to Java int for field [name] with value [\"Charlize Theron\"]"
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object CompositeTypesSpec {
  final case class Person(name: String, born: Int, extra: Option[Int])

  final case class PersonIntName(name: Int, born: Int, extra: Option[Int])

  final case class PersonWithNestedMap(name: String, map: Map[String, Int])

  final case class FooBar(foo: Int, bar: String)

  final case class PersonWithNestedClass(name: String, fooBar: FooBar)
}
