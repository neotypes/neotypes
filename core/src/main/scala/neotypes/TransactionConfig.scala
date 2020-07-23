package neotypes

import internal.utils.toJavaDuration
import types.QueryParam

import org.neo4j.driver.{TransactionConfig => NeoTransactionConfig}

import scala.concurrent.duration.FiniteDuration

/** Scala friendly factory for instances of [[org.neo4j.driver.TransactionConfig]].
  *
  * @see [[https://neo4j.com/docs/operations-manual/current/monitoring/transaction-management/ Neo4j Transaction Management]].
  */
object TransactionConfig {
  /** Creates a new TransactionConfig using the provided timeout and metadata. */
  def apply(timeout: FiniteDuration, metadata: Map[String, QueryParam]): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withTimeout(toJavaDuration(timeout))
      .withMetadata(QueryParam.toJavaMap(metadata))
      .build()

  /** Creates a new TransactionConfig using the provided timeout. */
  def apply(timeout: FiniteDuration): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withTimeout(toJavaDuration(timeout))
      .build()

  /** Creates a new TransactionConfig using the provided metadata. */
  def apply(metadata: Map[String, QueryParam]): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withMetadata(QueryParam.toJavaMap(metadata))
      .build()
}
