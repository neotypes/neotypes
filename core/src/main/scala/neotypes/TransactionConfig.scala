package neotypes

import internal.utils.toJavaDuration
import types.QueryParam

import org.neo4j.driver.{AccessMode, SessionConfig => NeoSessionConfig, TransactionConfig => NeoTransactionConfig}

import scala.concurrent.duration.FiniteDuration

/** Scala friendly builder for instances of
  * [[org.neo4j.driver.TransactionConfig]] and [[org.neo4j.driver.SessionConfig]].
  *
  * @see [[https://neo4j.com/docs/operations-manual/current/monitoring/transaction-management/ Neo4j Transaction Management]].
  */
final class TransactionConfig private (
  accessMode: Option[AccessMode] = None,
  database: Option[String] = None,
  metadata: Option[Map[String, QueryParam]] = None,
  timeout: Option[FiniteDuration] = None
) {
  private[neotypes] def getConfigs: (NeoSessionConfig, NeoTransactionConfig) = {
    val sessionConfigBuilder = NeoSessionConfig.builder
    val transactionConfigBuilder = NeoTransactionConfig.builder

    accessMode.foreach(am => sessionConfigBuilder.withDefaultAccessMode(am))
    database.foreach(db => sessionConfigBuilder.withDatabase(db))
    metadata.foreach(md => transactionConfigBuilder.withMetadata(QueryParam.toJavaMap(md)))
    timeout.foreach(d => transactionConfigBuilder.withTimeout(toJavaDuration(d)))

    (sessionConfigBuilder.build(), transactionConfigBuilder.build())
  }

  private def copy(
    accessMode: Option[AccessMode] = this.accessMode,
    database: Option[String] = this.database,
    metadata: Option[Map[String, QueryParam]] = this.metadata,
    timeout: Option[FiniteDuration] = this.timeout
  ): TransactionConfig =
    new TransactionConfig(accessMode, database, metadata, timeout)

  def withAccessMode(accessMode: AccessMode): TransactionConfig =
    copy(accessMode = Some(accessMode))

  def withDatabase(database: String): TransactionConfig =
    copy(database = Some(database))

  def withMetadata(metadata: Map[String, QueryParam]): TransactionConfig =
    copy(metadata = Some(metadata))

  def withTimeout(timeout: FiniteDuration): TransactionConfig =
    copy(timeout = Some(timeout))
}

object TransactionConfig {
  def default: TransactionConfig = new TransactionConfig()
}
