package neotypes
package query

import internal.utils.createQuery
import model.query.QueryParam

import scala.quoted.*

object CypherStringInterpolator:
  type Res = Either[String, QueryArg]

  def macroImpl(argsExpr: Expr[Seq[Any]], clz: Expr[StringContext])(using quotes: Quotes): Expr[DeferredQueryBuilder] =
    import quotes.reflect.*

    def invert[T: Type](exprs: Expr[Seq[T]]): List[Expr[T]] =
      exprs match {
        case Varargs(props)                => props.toList
        case '{ Seq(${ Varargs(props) }) } => props.view.map(_.asExprOf[T]).toList
        case _                             => report.errorAndAbort(s"Unable to match ${exprs.show}")
      }

    def argMapperFor(tt: TypeRepr) =
      TypeRepr.of[QueryArgMapper].appliedTo(tt)

    def asRightQueryArg(expr: Expr[Any]): Expr[Res] =
      val tt = expr.asTerm.tpe.widen

      tt.asType match
        case '[scala.Null] =>
          '{ Right(QueryArg.Param(QueryParam.NullValue)) }

        case _ =>
          Implicits.search(argMapperFor(tt)) match
            case iss: ImplicitSearchSuccess =>
              val e = Apply(Select.unique(iss.tree, "toArg"), expr.asTerm :: Nil).asExprOf[QueryArg]
              '{ Right($e) }

            case isf: ImplicitSearchFailure =>
              val e = Expr(isf.explanation)
              '{ compiletime.error($e) }

    def parts: List[Expr[Boolean => Res]] =
      invert(argsExpr).map { (expr: Expr[Any]) =>
        lazy val qarg: Expr[Res] = asRightQueryArg(expr)

        lazy val selectToString =
          Select.unique(expr.asTerm, "toString")

        lazy val toStringExpr =
          selectToString.signature match
            case None =>
              // handle the case where someone overrides `toString()` without parenthesis.
              // If we "apply" `toString` of the overridden one,
              // it causes a compile error "method toString in class X does not take parameter"
              // during macro expansion.
              // For example, `cats.data.Chain` overrides to string as `override def toString: String = show(Show.fromToString)`.
              // Without this case, the test for Chain in BaseCatsDataSpec won't compile.
              selectToString.asExprOf[String]

            case Some(_) =>
              selectToString.appliedToArgs(Nil).asExprOf[String]

        '{ (b: Boolean) => if (b) then $qarg else Left($toStringExpr) }
      }

    val partsPacked: Expr[List[Boolean => Res]] = Expr.ofList(parts)

    val stringFragmentsPartitionedByInterpolationKind: Expr[List[Either[String, String]]] = '{
      $clz
        .parts
        .map { part =>
          // 1. string before interpolation: "foo #${} bar" or "foo bar#${}"
          //    In this case, `#` is a marker for interpolation that should be dropped.
          // 2. the hashtag at the end of string: "foo bar#"
          //    It is unusual that cypher query ends with `#`
          //    so, optimistically use `part.init` here.
          if (part.endsWith("#"))
            // Left means "nth part is `part.init` and the interpolation after the nth part is a plain value"
            Left(part.init)
          else
            // Right means "nth part is`part` and the interpolation after the nth part (if any) is a query parameter"
            Right(part)
        }
        .toList
    }

    val interleaved: Expr[List[Res]] =
      '{ Interleave($partsPacked, $stringFragmentsPartitionedByInterpolationKind) }

    '{ createQuery($interleaved: _*) }
end CypherStringInterpolator

object Interleave:
  import CypherStringInterpolator.Res

  /** @param interpolations
    *   a list of interpolation generator functions. If a function receives `true` it should generate QueryArg
    *   representation and otherwise it should generate String representation.
    * @param stringFragments
    *   a list of string splitted by interpolation and distinguished by interpolation kind. If the n-th element of
    *   `stringFragments` is `Right`, it implies the next adjacent interpolation should be a `QueryArg` interpolation.
    *   If the n-th element of `stringFragments` is `Left`, it implies the next adjacent interpolation should be a plain
    *   value interpolation.
    */
  def apply(
    interpolations: List[Boolean => Res],
    stringFragments: List[Either[String, String]]
  ): List[Res] =
    interleave(interpolations, stringFragments, acc = List.empty)

  private[this] def interleave(
    interpolations: List[Boolean => Res],
    stringFragments: List[Either[String, String]],
    acc: List[Res]
  ): List[Res] =
    (stringFragments, interpolations) match
      case (Nil, Nil) =>
        acc

      case (Nil, interps) =>
        // this should be always query interpolations because if there is a plain value interpolation, there should be `#` prefix.
        acc reverse_::: (interps.map(_.apply(true)))

      case (Left(nthPartExpectPlainValueInsert) :: tail, interp :: interps) =>
        interleave(interps, tail, interp(false) :: Left(nthPartExpectPlainValueInsert) :: acc)

      case (Right(nthPartExpectQueryValueInsert) :: tail, interp :: interps) =>
        interleave(interps, tail, interp(true) :: Left(nthPartExpectQueryValueInsert) :: acc)

      case (Right(theLastPart) :: Nil, Nil) =>
        (Left(theLastPart) :: acc).reverse

      case (Left(theLastPartIsAHashTag) :: Nil, Nil) =>
        (Left(theLastPartIsAHashTag) :: acc).reverse

      case _ =>
        // this should be unreachable
        throw new Exception("interpolation value number mismatch")
end Interleave
