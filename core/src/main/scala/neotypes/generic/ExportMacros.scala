package neotypes.generic

import neotypes.mappers.ResultMapper

import scala.reflect.macros.blackbox

private[generic] class ExportMacros(val c: blackbox.Context) {

  import c.universe._

  final def exportResultMapper[D[x] <: ResultMapper[x], A](implicit D: c.WeakTypeTag[D[_]],
                                                     A: c.WeakTypeTag[A]): c.Expr[Exported[ResultMapper[A]]] = {
    val target = appliedType(D.tpe.typeConstructor, A.tpe)

    c.typecheck(q"_root_.shapeless.lazily[$target]", silent = false) match {
      case EmptyTree => c.abort(c.enclosingPosition, s"Unable to infer value of type $target")
      case t =>
        c.Expr[Exported[ResultMapper[A]]](
          q"new _root_.neotypes.generic.Exported($t: _root_.neotypes.mappers.ResultMapper[$A])"
        )
    }
  }

}
