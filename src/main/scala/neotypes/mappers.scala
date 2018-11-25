package neotypes

import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.summary.ResultSummary

import scala.reflect.ClassTag

package object mappers {

  trait ResultMapper[T] {
    def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, T]
  }

  trait ValueMapper[T] {
    def to(fieldName: String, value: Option[Value]): Either[Throwable, T]
  }

  trait ExecutionMapper[T] {
    def to(resultSummary: ResultSummary): Either[Throwable, T]
  }

  case class TypeHint(isTuple: Boolean)

  object TypeHint {
    def apply[T](classTag: ClassTag[T]): TypeHint = new TypeHint(classTag.runtimeClass.getName.startsWith("scala.Tuple"))
  }
}