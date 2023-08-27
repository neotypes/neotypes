package neotypes
package query

import scala.quoted.*
import neotypes.internal.utils.createQuery
import neotypes.model.query.QueryParam

final case class CypherStringInterpolator(private val sc: StringContext) extends AnyVal {
  inline def c(inline args: Any*): DeferredQueryBuilder = ${ CypherStringInterpolator.macroImpl('args, 'sc) }
}

object CypherStringInterpolator {

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
          '{
            Right(QueryArg.Param(QueryParam.NullValue))
          }
        case _ =>
          Implicits.search(argMapperFor(tt)) match {
            case iss: ImplicitSearchSuccess => {
              val e = Apply(Select.unique(iss.tree, "toArg"), expr.asTerm :: Nil)
                .asExprOf[QueryArg]
              '{ Right($e) }

            }
            case isf: ImplicitSearchFailure =>
              val e = Expr(isf.explanation)
              '{ compiletime.error($e) }
          }

    def parts: List[Expr[Boolean => Res]] = {
      val inverted = invert(argsExpr)
      inverted
        .map { (expr: Expr[Any]) =>
          lazy val qarg: Expr[Res] = asRightQueryArg(expr)
          '{ (b: Boolean) =>
            if (b)
              $qarg
            else Left($expr.toString)
          }
        }
    }

    val partsPacked: Expr[List[Boolean => Res]] = Expr.ofList(parts)

    val stringFragmentsPartitionedByInterpolationKind: Expr[List[Either[String, String]]] = '{
      $clz
        .parts
        .map { part =>
          if (part.endsWith("#"))
            // 1. string before interpolation: "foo #${} bar" or "foo bar#${}"
            //    In this case, `#` is a marker for interpolation that should be dropped.
            // 2. the hashtag at the end of string: "foo bar#"
            //    It is unusual that cypher query ends with `#`
            //    so, optimistically use `part.init` here.
            Left(
              part.init
            ) // Left means "nth part is `part.init` and the interpolation after the nth part is a plain value"
          else
            // Right means "nth part is`part` and the interpolation after the nth part (if any) is a query parameter"
            Right(part)
        }
        .toList
    }
    val interleaved: Expr[List[Res]] =
      '{
        Interleave($partsPacked, $stringFragmentsPartitionedByInterpolationKind)
      }

    '{
      createQuery($interleaved: _*)
    }
}

object Interleave {
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
  def apply(interpolations: List[Boolean => Res], stringFlagments: List[Either[String, String]]): List[Res] =
    interleave(interpolations, stringFlagments, List.empty)
  private[this] def interleave(
    interpolations: List[Boolean => Res],
    stringFlagments: List[Either[String, String]],
    acc: List[Res]
  ): List[Res] =
    (stringFlagments, interpolations) match
      case (Nil, Nil) => acc
      // this should be always query interpolations because if there is a plain value interpolation, there should be `#` prefix.
      case (Nil, interps) => acc.reverse ++ (interps.map(_.apply(true)))
      case (Left(nthPartExpectPlainValueInsert) :: tail, interp :: interps) =>
        interleave(interps, tail, interp(false) :: Left(nthPartExpectPlainValueInsert) :: acc)
      case (Right(nthPartExpectQueryValueInsert) :: tail, interp :: interps) =>
        interleave(interps, tail, interp(true) :: Left(nthPartExpectQueryValueInsert) :: acc)
      case (Right(theLastPart) :: Nil, Nil) => (Left(theLastPart) :: acc).reverse
      case (Left(theLastPartIsAHashTag) :: Nil, Nil) =>
        (Left(theLastPartIsAHashTag) :: acc).reverse
      case (Right(partExpectQueryValueInsert) :: _, Nil) =>
        // this should be unreachable
        throw new Exception(s"interpolation value number mismatch")
      case (Left(partExpectPlainValueInsert) :: _, Nil) =>
        // this should be unreachable
        throw new Exception(s"interpolation value number mismatch")
}
