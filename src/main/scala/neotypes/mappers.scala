package neotypes

import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.summary.ResultSummary

package object mappers {

  trait ResultMapper[T] {
    def to(value: Seq[(String, Value)]): Either[Throwable, T]
  }

  trait ValueMapper[T] {
    def to(fieldName: String, value: Option[Value]): Either[Throwable, T]
  }

  trait ExecutionMapper[T] {
    def to(resultSummary: ResultSummary): Either[Throwable, T]
  }

}