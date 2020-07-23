package neotypes.internal

import java.time.{Duration => JDuration}
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

package object utils {
  /** Used to swallow unused warnings. */
  @inline
  private[neotypes] final def void(as: Any*): Unit = (as, ())._2

  /** Convert a Scala `FiniteDuration` to a Java duration. Note that the Scala duration keeps the
    * time unit it was created with, while a Java duration always is a pair of seconds and nanos,
    * so the unit it lost.
    */
  private[neotypes] def toJavaDuration(duration: FiniteDuration): JDuration = {
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

}
