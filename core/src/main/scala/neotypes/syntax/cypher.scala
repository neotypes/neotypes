package neotypes
package implicits.syntax

trait CypherSyntax {
  implicit final def neotypesSyntaxCypherStringInterpolator(sc: StringContext): CypherStringInterpolator =
    new CypherStringInterpolator(sc)
}
