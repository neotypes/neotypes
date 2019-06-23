package neotypes
package implicits.syntax

import org.neo4j.driver.v1.{Session => NSession}

import scala.language.{higherKinds, implicitConversions}

private[neotypes] trait SessionSyntax {
  implicit final def neotypesSyntaxSessionId(session: NSession): SessionIdOps =
    new SessionIdOps(session)
}

final class SessionIdOps(private val session: NSession) extends AnyVal {
  def asScala[F[_]: Async]: Session[F] =
    new Session[F](session)
}
