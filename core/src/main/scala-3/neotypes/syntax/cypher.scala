package neotypes
package syntax

import query.{DeferredQueryBuilder, CypherStringInterpolator}

import scala.quoted.*

trait CypherSyntax:
  extension (sc: StringContext)
    inline def c(inline args: Any*): DeferredQueryBuilder =
      ${ CypherStringInterpolator.macroImpl('args, 'sc) }
