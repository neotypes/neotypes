package neotypes
package implicits.mappers

import mappers.ExecutionMapper

import org.neo4j.driver.summary.ResultSummary

trait ExecutionMappers {
  implicit final val ResultSummaryExecutionMapper: ExecutionMapper[ResultSummary] =
    new ExecutionMapper[ResultSummary] {
      override def to(resultSummary: ResultSummary): Either[Throwable, ResultSummary] =
        Right(resultSummary)
    }

  implicit final val UnitExecutionMapper: ExecutionMapper[Unit] =
    new ExecutionMapper[Unit] {
      override def to(resultSummary: ResultSummary): Either[Throwable, Unit] =
        Right(())
    }
}
