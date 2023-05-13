package neotypes
package syntax

import query.CypherStringInterpolator

trait CypherSyntax {
  implicit final def neotypesSyntaxCypherStringInterpolator(sc: StringContext): CypherStringInterpolator =
    new CypherStringInterpolator(sc)
}
