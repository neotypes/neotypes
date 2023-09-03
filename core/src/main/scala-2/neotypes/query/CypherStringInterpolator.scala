package neotypes
package query

import scala.reflect.macros.blackbox

final class CypherStringInterpolator(private val sc: StringContext) extends AnyVal {
  def c(args: Any*): DeferredQueryBuilder = macro CypherStringInterpolator.macroImpl
}

object CypherStringInterpolator {
  def macroImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DeferredQueryBuilder] = {
    import c.universe._

    val Apply(_, List(Apply(_, rawParts))) = c.prefix.tree

    val queryData: List[Tree] = {
      val params = args.iterator
      val queries = rawParts.iterator

      @annotation.tailrec
      def loop(paramNext: Boolean, acc: List[Tree]): List[Tree] =
        if (paramNext && params.hasNext) {
          val nextParam = params.next().tree
          val tpe = nextParam.tpe.widen

          val newTree =
            if (nextParam.symbol eq null)
              q"Right(_root_.neotypes.query.QueryArg.Param(_root_.neotypes.model.query.QueryParam.NullValue))"
            else
              q"Right(_root_.neotypes.query.QueryArgMapper[${tpe}].toArg(${nextParam}))"

          loop(
            paramNext = false,
            newTree :: acc
          )
        } else if (queries.hasNext) {
          val Literal(Constant(query: String)) = queries.next()

          if (query.endsWith("#"))
            loop(
              paramNext = false,
              q"Left(${params.next().tree}.toString)" :: q"Left(${query.init})" :: acc
            )
          else
            loop(
              paramNext = true,
              q"Left(${query})" :: acc
            )
        } else {
          acc.reverse
        }

      loop(
        paramNext = false,
        acc = List.empty
      )
    }

    c.Expr(
      q"_root_.neotypes.internal.utils.createQuery(..${queryData})"
    )
  }
}
