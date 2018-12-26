package neotypes

import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.summary.ResultSummary

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

package object mappers {

  @implicitNotFound("Could not find the ResultMapper for ${T}")
  trait ResultMapper[T] {
    def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, T]
  }

  @implicitNotFound("Could not find the ValueMapper for ${T}")
  trait ValueMapper[T] {
    def to(fieldName: String, value: Option[Value]): Either[Throwable, T]
  }

  @implicitNotFound("Could not find the ExecutionMapper for ${T}")
  trait ExecutionMapper[T] {
    def to(resultSummary: ResultSummary): Either[Throwable, T]
  }

  case class TypeHint(isTuple: Boolean)

  object TypeHint {
    def apply[T](classTag: ClassTag[T]): TypeHint = new TypeHint(classTag.runtimeClass.getName.startsWith("scala.Tuple"))
  }
}