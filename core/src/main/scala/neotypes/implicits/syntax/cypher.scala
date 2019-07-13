package neotypes
package implicits.syntax

import scala.language.implicitConversions

trait CypherSyntax {
  implicit final def neotypesSyntaxChyperStringInterpolator(sc: StringContext): CypherStringInterpolator =
    new CypherStringInterpolator(sc)
}
