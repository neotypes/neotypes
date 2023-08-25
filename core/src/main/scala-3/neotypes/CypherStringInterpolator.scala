package neotypes
package query

import scala.quoted.*
import internal.utils.createQuery

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
        case '{ Seq(${ Varargs(props) }) } => props.toList.map(_.asExprOf[T])
        case _                             => println(s"Unable to match ${exprs.show} "); null
      }

    def argMapperFor(tt: TypeRepr) =
      TypeRepr.of[QueryArgMapper].appliedTo(tt)

    def asRightQueryArg(expr: Expr[Any]): Expr[Res] =
      val tt = expr.asTerm.tpe.widen
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

    val boolsAndRes: Expr[(List[Boolean], List[Res])] = '{
      val (bs, rs) = $clz
        .parts
        .foldLeft((List.empty[Boolean], List.empty[Res])) { case ((hashTags, res), s) =>
          if (s.endsWith("#"))
            (false :: hashTags) -> (Left(s.init) :: res)
          else
            (true :: hashTags) -> (Left(s) :: res)
        }
      (bs.reverse -> rs.reverse)
    }

    val interleaved: Expr[List[Res]] =
      '{
        Interleave($partsPacked, $boolsAndRes._2, $boolsAndRes._1)
      }
    '{ createQuery($interleaved: _*) }
}

object Interleave {
  import CypherStringInterpolator.Res
  def apply(parts: List[Boolean => Res], queries: List[Res], endWithHashTags: List[Boolean]): List[Res] =
    interleave(parts, queries, endWithHashTags, List.empty)
  private[this] def interleave(
    parts: List[Boolean => Res],
    queries: List[Res],
    endWithHashTags: List[Boolean],
    acc: List[Res]
  ): List[Res] =
    (queries, parts, endWithHashTags) match {
      case (Nil, l2, b)                   => acc.reverse ++ (l2 zip b).map { case (e, b) => e(b) }
      case (l1, Nil, _)                   => acc.reverse ++ l1
      case (h1 :: t1, h2 :: t2, bh :: bt) => interleave(t2, t1, bt, h2(bh) +: h1 +: acc)
      case _                              => acc
    }
}
