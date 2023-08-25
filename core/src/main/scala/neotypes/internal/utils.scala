package neotypes
package internal

import scala.collection.Factory
import scala.collection.mutable.Builder
import neotypes.query.QueryArg
import neotypes.model.query.QueryParam
import neotypes.query.DeferredQueryBuilder

private[neotypes] object utils {

  /** Used to swallow warnings. */
  @inline
  final def void(as: Any*): Unit = (as, ())._2

  /** Applies a function to all elements of an iterable, accumulating all success or stopping at the first failure.
    */
  def traverseAs[A, B, C, E](
    factory: Factory[B, C]
  )(
    col: IterableOnce[A]
  )(
    f: A => Either[E, B]
  ): Either[E, C] = {
    val iter = col.iterator

    @annotation.tailrec
    def loop(acc: Builder[B, C]): Either[E, C] =
      if (iter.hasNext) {
        f(iter.next()) match {
          case Right(value) => loop(acc = acc += value)
          case Left(e)      => Left(e)
        }
      } else {
        Right(acc.result())
      }

    loop(acc = factory.newBuilder)
  }
  def createQuery(queryData: Either[String, QueryArg]*): DeferredQueryBuilder = {
    var subQueryIndex = 0

    new DeferredQueryBuilder(parts = queryData.toList.flatMap {
      case Left(query) =>
        DeferredQueryBuilder.Query(query) :: Nil

      case Right(QueryArg.Param(param)) =>
        DeferredQueryBuilder.Param(param) :: Nil

      case Right(QueryArg.Params(params)) =>
        caseClassParts(params)

      case Right(QueryArg.QueryBuilder(builder)) =>
        subQueryIndex += 1
        subQueryParts(queryBuilder = builder, index = subQueryIndex)
    })
  }
  private val comma = DeferredQueryBuilder.Query(",")

  private def caseClassParts(params: List[(String, QueryParam)]): List[DeferredQueryBuilder.Part] = {
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

    loop(params, acc = Nil)
  }
  private def subQueryParts(queryBuilder: DeferredQueryBuilder, index: Int): List[DeferredQueryBuilder.Part] = {
    val (originalQuery, originalParams, originalLocations) = queryBuilder.build()
    val (prefixedQuery, prefixedParams, prefixedLocations) =
      withParameterPrefix(s"q${index}_", originalQuery, originalParams, originalLocations)

    val query = DeferredQueryBuilder.Query(prefixedQuery, prefixedLocations)
    val params = prefixedParams
      .iterator
      .map { case (name, value) =>
        DeferredQueryBuilder.SubQueryParam(name, value)
      }
      .toList

    query :: params
  }
  private def withParameterPrefix(
    prefix: String,
    query: String,
    params: Map[String, QueryParam],
    paramLocations: List[Int]
  ): (String, Map[String, QueryParam], List[Int]) = {
    val newParams = params.map { case (k, v) =>
      (prefix + k) -> v
    }

    val newLocations = paramLocations.sorted.zipWithIndex.map { case (location, i) =>
      location + (i * prefix.length)
    }

    val newQuery = newLocations.foldLeft(query) { case (query, location) =>
      query.patch(location + 1, prefix, 0)
    }

    (
      newQuery,
      newParams,
      newLocations
    )
  }
}
