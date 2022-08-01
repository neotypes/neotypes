package neotypes

import neotypes.mappers.{ResultMapper, TypeHint, ValueMapper}
import org.neo4j.driver.internal.value.{IntegerValue, StringValue}
import org.neo4j.driver.Value
import org.scalatest.freespec.AnyFreeSpec

final class MapperSpec extends AnyFreeSpec {
  import MapperSpec.MyCaseClass

  "ResultMapper" - {
    "constructors" - {
      "should summon implicit ResultMapper instances" in {
        val strMapper = ResultMapper[String]
        assert(strMapper == ResultMapper.StringResultMapper)
      }
      "should create an instance based on a function" in {
        def myParsingFunction(vals: List[(String, Value)], typeHint: Option[TypeHint]) = Right("function works")
        val myInstanceMapper = ResultMapper.instance(myParsingFunction)
        val result = myInstanceMapper.to(List(("value", new StringValue("function doesn't work"))), None)
        assert(result == Right("function works"))
      }
      "should return a constant result value" in {
        val constMapper = ResultMapper.const[String]("const")
        val result = constMapper.to(List(("value", new StringValue("not const"))), None)
        assert(result == Right("const"))
      }
      "should return a constant failure" in {
        val myException = new Exception("Example Exception")
        val failureMapper = ResultMapper.failed[String](myException)
        val result = failureMapper.to(List(("value", new StringValue("value"))), None)
        assert(result == Left(myException))
      }
    }
    "should allow defining a secondary mapper" in {
      val failingMapper = new ResultMapper[String] {
        override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, String] =
          Left(new Exception)
      }
      val result = failingMapper.or(ResultMapper.StringResultMapper).to(List(("value", new StringValue("string"))), None)
      assert(result == Right("string"))
    }

    "should be mappable" in {
      val caseClassMapper = ResultMapper[String].map(x => MyCaseClass(x + "2"))
      val result = caseClassMapper.to(List(("value", new StringValue("1"))), None)
      assert(result == Right(MyCaseClass("12")))
    }

    "should be flatmappable" in {
      val flatMappedMapper = ResultMapper[String].flatMap { str =>
        new ResultMapper[Int] {
          override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Int] =
            Right(str.length)
        }
      }
      val result = flatMappedMapper.to(List(("value", new StringValue("twelve chars"))), None)
      assert(result == Right(12))
    }

    "should derive a product mapper" in {
      val lengthOfStringMapper = ResultMapper[String].map(_.length)
      val productMapper = ResultMapper.StringResultMapper.product(lengthOfStringMapper)
      val result = productMapper.to(List(("value", new StringValue("twelve chars"))), None)
      assert(result == Right("twelve chars" -> 12))
    }

    "should derive an either mapper" in {
      val eitherIntOrStringMapper = ResultMapper[Int].either(ResultMapper.StringResultMapper)
      val resultInt = eitherIntOrStringMapper.to(List(("value", new IntegerValue(1))), None)
      val resultString = eitherIntOrStringMapper.to(List(("value", new StringValue("string"))), None)
      assert(resultInt == Right(Left(1)))
      assert(resultString == Right(Right("string")))
    }
  }

  "ValueMapper" - {
    "constructors" - {
      "should summon implicit ValueMapper instances" in {
        val strMapper = ValueMapper[String]
        assert(strMapper == ValueMapper.StringValueMapper)
      }
      "should create an instance based on a function" in {
        def myParsingFunction(name: String, value: Option[Value]) = Right("function works")
        val myInstanceMapper = ValueMapper.instance(myParsingFunction)
        val result = myInstanceMapper.to("value", Some(new StringValue("function doesn't work")))
        assert(result == Right("function works"))
      }
      "should return a constant result value" in {
        val constMapper = ValueMapper.const("const")
        val result = constMapper.to("value", Some(new StringValue("not const")))
        assert(result == Right("const"))
      }
      "should return a constant failure" in {
        val myException = new Exception("Example Exception")
        val failureMapper = ValueMapper.failed[String](myException)
        val result = failureMapper.to("value", Some(new StringValue("value")))
        assert(result == Left(myException))
      }
    }
    "should allow defining a secondary mapper" in {
      val failingMapper = ValueMapper.failed[String](new Exception)
      val result = failingMapper.or(ValueMapper[String]).to("value", Some(new StringValue("string")))
      assert(result == Right("string"))
    }

    "should be mappable" in {
      val caseClassMapper = ValueMapper[String].map(x => MyCaseClass(x + "2"))
      val result = caseClassMapper.to("value", Some(new StringValue("1")))
      assert(result == Right(MyCaseClass("12")))
    }

    "should be flatmappable" in {
      val flatMappedMapper = ValueMapper[String].flatMap { str =>
        new ValueMapper[Int] {
          override def to(fieldName: String, value: Option[Value]): Either[Throwable, Int] =
            Right(str.length)
        }
      }
      val result = flatMappedMapper.to("value", Some(new StringValue("twelve chars")))
      assert(result == Right(12))
    }

    "should be able to derive a product mapper" in {
      val lengthOfStringMapper = ValueMapper[String].map(_.length)
      val productMapper = ValueMapper.StringValueMapper.product(lengthOfStringMapper)
      val result = productMapper.to("value", Some(new StringValue("twelve chars")))
      assert(result == Right("twelve chars" -> 12))
    }

    "should derive an either mapper" in {
      val eitherIntOrStringMapper = ValueMapper[Int].either(ValueMapper[String])
      val resultInt = eitherIntOrStringMapper.to("value", Some(new IntegerValue(1)))
      val resultString = eitherIntOrStringMapper.to("value", Some(new StringValue("string")))
      assert(resultInt == Right(Left(1)))
      assert(resultString == Right(Right("string")))
    }
  }
}

object MapperSpec {
  final case class MyCaseClass(value: String)
}
