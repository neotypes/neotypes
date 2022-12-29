package neotypes
package generic

import query.QueryArgMapper

import scala.reflect.macros.blackbox

private[generic] class ExportMacros(val c: blackbox.Context) {
  import c.universe._

  final def exportCaseClassArgMapper[C[x] <: QueryArgMapper[x], A](
    implicit C: c.WeakTypeTag[C[_]], A: c.WeakTypeTag[A]
  ): c.Expr[Exported[QueryArgMapper[A]]] = {
    val target = appliedType(C.tpe.typeConstructor, A.tpe)

    c.typecheck(q"_root_.shapeless.lazily[$target]", silent = false) match {
      case EmptyTree => c.abort(c.enclosingPosition, s"Unable to infer value of type $target")
      case t =>
        c.Expr[Exported[QueryArgMapper[A]]](
          q"new _root_.neotypes.Exported($t: _root_.neotypes.QueryArgMapper[$A])"
        )
    }
  }
}
