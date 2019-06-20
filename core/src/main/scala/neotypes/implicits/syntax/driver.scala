package neotypes
package implicits.syntax

import org.neo4j.driver.v1.{Driver => NDriver}

import scala.language.{higherKinds, implicitConversions}

private[neotypes] trait DriverSyntax {
  implicit final def neotypesSyntaxDriverId(driver: NDriver): DriverIdOps =
    new DriverIdOps(driver)
}

final class DriverIdOps(private val driver: NDriver) extends AnyVal {
  def asScala[F[_]: Async]: Driver[F] =
    new Driver[F](driver)
}
