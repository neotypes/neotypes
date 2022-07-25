package neotypes

import mappers.ParameterMapper
import types.QueryParam

import scala.reflect.macros.blackbox

final class CypherStringInterpolator(private val sc: StringContext) extends AnyVal {
  def c(args: Any*): DeferredQueryBuilder = macro CypherStringInterpolator.macroImpl
}

object CypherStringInterpolator {
  def createQuery(queryData: Either[String, QueryArg]*): DeferredQueryBuilder = {
    var subQueryIndex = 0

    new DeferredQueryBuilder(parts = queryData.toList.flatMap {
      case Left(query) =>
        DeferredQueryBuilder.Query(query) :: Nil

      case Right(QueryArg.Param(param)) =>
        DeferredQueryBuilder.Param(param) :: Nil

      case Right(QueryArg.CaseClass(params)) =>
        caseClassParts(params)

      case Right(QueryArg.QueryBuilder(builder)) =>
        subQueryIndex += 1
        subQueryParts(queryBuilder = builder, index = subQueryIndex)
    })
  }

  private val comma = DeferredQueryBuilder.Query(",")

  private def caseClassParts(params: Map[String, QueryParam]): List[DeferredQueryBuilder.Part] = {
    @annotation.tailrec
    def loop(input: List[(String, QueryParam)], acc: List[DeferredQueryBuilder.Part]): List[DeferredQueryBuilder.Part] =
      input match {
        case (label, queryParam) :: tail =>
          val query = DeferredQueryBuilder.Query(label + ": ")
          val param = DeferredQueryBuilder.Param(queryParam)

          loop(
            input = tail,
            comma :: param :: query :: acc
          )

        case Nil =>
          acc.tail.reverse
      }

    loop(params.toList, acc = Nil)
  }

  private def withParameterPrefix(
    prefix: String,
    query: String,
    params: Map[String, QueryParam],
    paramLocations: List[Int]
  ): (String, Map[String, QueryParam], List[Int]) = {
    val newParams = params.map {
      case (k, v) =>
        (prefix + k) -> v
    }

    val newLocations = paramLocations.sorted.zipWithIndex.map {
      case (location, i) =>
        location + (i * prefix.length)
    }

    val newQuery = newLocations.foldLeft(query) {
      case (query, location) =>
        query.patch(location + 1, prefix, 0)
    }

    (
      newQuery,
      newParams,
      newLocations
    )
  }

  private def subQueryParts(queryBuilder: DeferredQueryBuilder, index: Int): List[DeferredQueryBuilder.Part] = {
    val (originalQuery, originalParams, originalLocations) = queryBuilder.build()
    val (prefixedQuery, prefixedParams, prefixedLocations) = withParameterPrefix(s"q${index}_", originalQuery, originalParams, originalLocations)

    val query = DeferredQueryBuilder.Query(prefixedQuery, prefixedLocations)
    val params = prefixedParams.iterator.map {
      case (name, value) =>
        DeferredQueryBuilder.SubQueryParam(name, value)
    }.toList

    query :: params
  }

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

          loop(
            paramNext = false,
            q"Right(_root_.neotypes.QueryArgMapper[${tpe}].toArg(${nextParam}))" :: acc
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
      q"_root_.neotypes.CypherStringInterpolator.createQuery(..${queryData})"
    )
  }
}

sealed trait QueryArg
object QueryArg {
  final case class Param(param: QueryParam) extends QueryArg
  final case class CaseClass(params: Map[String, QueryParam]) extends QueryArg
  final case class QueryBuilder(builder: DeferredQueryBuilder) extends QueryArg
}

@annotation.implicitNotFound(
"""
Could not find the ArgMapper for ${A}.

Make sure ${A} is a supported type: https://neotypes.github.io/neotypes/types.html
If ${A} is a case class then `import neotypes.generic.auto._` for the automated derivation,
or use the semiauto one: `implicit val instance: CaseClassArgMapper[A] = neotypes.generic.semiauto.deriveCaseClassArgMapper`
"""
)
trait QueryArgMapper[A] {
  def toArg(value: A): QueryArg
}
object QueryArgMapper extends QueryArgMappers {
  def apply[A](implicit ev: QueryArgMapper[A]): ev.type = ev
}

trait QueryArgMappers extends QueryArgMappersLowPriority {
  implicit final def fromParameterMapper[A](implicit mapper: ParameterMapper[A]): QueryArgMapper[A] =
    new QueryArgMapper[A] {
      override def toArg(value: A): QueryArg =
        QueryArg.Param(mapper.toQueryParam(value))
    }

  implicit final val deferredQueryBuilderArgMapper: QueryArgMapper[DeferredQueryBuilder] =
    new QueryArgMapper[DeferredQueryBuilder] {
      override def toArg(value: DeferredQueryBuilder): QueryArg =
        QueryArg.QueryBuilder(value)
    }
}

trait QueryArgMappersLowPriority {
  implicit final def exportedCaseClassArgMapper[A](implicit exported: Exported[QueryArgMapper[A]]): QueryArgMapper[A] =
    exported.instance
}
