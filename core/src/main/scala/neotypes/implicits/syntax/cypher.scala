package neotypes
package implicits.syntax

private[neotypes] trait CypherSyntax {
  implicit final def neotypesSyntaxChyperStringInterpolator(sc: StringContext): CypherStringInterpolator =
    new CypherStringInterpolator(sc)
}
