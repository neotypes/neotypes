package neotypes

import java.time.{Duration => JDuration}
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import types.QueryParam

import org.neo4j.driver.{TransactionConfig => NeoTransactionConfig}

import scala.concurrent.duration.FiniteDuration

object TransactionConfig {
  /** Convert a Scala `FiniteDuration` to a Java duration. Note that the Scala duration keeps the
    * time unit it was created with, while a Java duration always is a pair of seconds and nanos,
    * so the unit it lost.
    */
  private def toJavaDuration(duration: FiniteDuration): JDuration = {
    if (duration.length == 0) JDuration.ZERO
    else duration.unit match {
      case TimeUnit.NANOSECONDS => JDuration.ofNanos(duration.length)
      case TimeUnit.MICROSECONDS => JDuration.of(duration.length, ChronoUnit.MICROS)
      case TimeUnit.MILLISECONDS => JDuration.ofMillis(duration.length)
      case TimeUnit.SECONDS => JDuration.ofSeconds(duration.length)
      case TimeUnit.MINUTES => JDuration.ofMinutes(duration.length)
      case TimeUnit.HOURS => JDuration.ofHours(duration.length)
      case TimeUnit.DAYS => JDuration.ofDays(duration.length)
    }
  }

  def apply(timeout: FiniteDuration, metadata: Map[String, QueryParam]): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withTimeout(toJavaDuration(timeout))
      .withMetadata(QueryParam.toJavaMap(metadata))
      .build()

  def apply(timeout: FiniteDuration): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withTimeout(toJavaDuration(timeout))
      .build()

  def apply(metadata: Map[String, QueryParam]): NeoTransactionConfig =
    NeoTransactionConfig
      .builder
      .withMetadata(QueryParam.toJavaMap(metadata))
      .build()
}
