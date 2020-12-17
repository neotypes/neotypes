package neotypes

import neotypes.exceptions.IncoercibleException
import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.Value
import scala.jdk.CollectionConverters._

/** Base class for testing queries which produce complex types. */
final class CompositeTypesSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  behavior of s"Extracting complex types using: ${effectName}"

  import CompositeTypesSpec._

  it should "create a map from a node" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p"
      .query[Map[String, Value]]
      .single(d)
      .map { map =>
        assert(map("name").asString == "Charlize Theron")
        assert(map("born").asInt == 1975)
      }
  }

  it should "create a case class from a node" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p"
      .query[Person]
      .single(d)
      .map { person =>
        assert(person.name == "Charlize Theron")
        assert(person.born == 1975)
        assert(person.extra == None)
      }
  }

  it should "create a map from a map projection" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
      .query[Map[String, Value]]
      .single(d)
      .map { map =>
        assert(map("name").asString == "Charlize Theron")
        assert(map("born").asInt == 1975)
        assert(map("extra").asInt == 1)
      }
  }

  it should "create a map from a map projection with a nested map" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .name, map: { foo: 3, bar: 5 } }"
      .query[Map[String, Value]]
      .single(d)
      .map { map =>
        assert(map("name").asString == "Charlize Theron")
        val nestedMap = map("map").asMap[Value]((v: Value) => v).asScala
        assert(nestedMap("foo").asInt == 3)
        assert(nestedMap("bar").asInt == 5)
      }
  }

  it should "create a case class from a map projection" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .*, extra: 1 }"
      .query[Person]
      .single(d)
      .map { person =>
        assert(person.name == "Charlize Theron")
        assert(person.born == 1975)
        assert(person.extra == Some(1))
      }
  }

  it should "create a case class from a map projection with a nested map" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .name, map: { foo: 3, bar: 5 } }"
      .query[PersonWithNestedMap]
      .single(d)
      .map { person =>
        assert(person.name == "Charlize Theron")
        assert(person.map("foo") == 3)
        assert(person.map("bar") == 5)
      }
  }

  it should "create a nested case class from a map projection with a nested map" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .name, fooBar: { foo: 3, bar: '5' } }"
      .query[PersonWithNestedClass]
      .single(d)
      .map { person =>
        assert(person.name == "Charlize Theron")
        assert(person.fooBar.foo == 3)
        assert(person.fooBar.bar == "5")
      }
  }

  it should "create a List from a Neo4j LIST" in executeAsFuture { d =>
    "return [1, 2]"
      .query[List[Int]]
      .single(d)
      .map { list =>
        assert(list == List(1, 2))
      }
  }

  it should "create a nested List from a nested Neo4j LIST" in executeAsFuture { d =>
    "return [['a', 'b'], ['c', 'd']]"
      .query[List[List[String]]]
      .single(d)
      .map { list =>
        assert(list == List(List("a", "b"), List("c", "d")))
      }
  }

  it should "create a List from a map projection" in executeAsFuture { d =>
    "match (p:Person {name: 'Charlize Theron'}) return p { .name, .born, extra: 1 }"
      .query[List[Value]]
      .single(d)
      .map { list =>
        assert(list.length == 3)
        assert(list(0).asString == "Charlize Theron")
        assert(list(1).asInt == 1975)
        assert(list(2).asInt == 1)
      }
  }

  it should "construct an IncoercibleException message with a field name and value" in {
    recoverToExceptionIf[IncoercibleException] {
      executeAsFuture { d =>
        "match (p:Person {name: 'Charlize Theron'}) return p"
          .query[PersonIntName]
          .single(d)
      }
    } map { ex =>
      assert(ex.getMessage  == "Cannot coerce STRING to Java int for field [name] with value [\"Charlize Theron\"]")
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
