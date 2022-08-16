package neotypes
package generic

import mappers.ParameterMapper
import types.QueryParam

import shapeless.labelled.FieldType
import shapeless.ops.hlist.MapFolder
import shapeless.{HList, LabelledGeneric, Poly1, Witness}

trait DerivedCaseClassArgMapper[A] extends QueryArgMapper[A]

object DerivedCaseClassArgMapper {

  implicit def deriveCaseClassArgMapper[T <: Product, R <: HList](implicit ev: LabelledGeneric.Aux[T, R],
                                                            folder: MapFolder[R, Map[String, QueryParam], parameterMapper.type]): DerivedCaseClassArgMapper[T] =
    new DerivedCaseClassArgMapper[T] {
      override def toArg(value: T): QueryArg.CaseClass = {
        QueryArg.CaseClass(folder.apply(ev.to(value), Map.empty, _ ++ _))
      }
    }

  object parameterMapper extends LowPriority {
    implicit def optional[K <: Symbol, V: ParameterMapper](implicit key: Witness.Aux[K]): Case.Aux[FieldType[K, Option[V]], Map[String, QueryParam]] =
      at[FieldType[K, Option[V]]] { v =>
        (v: Option[V]).fold[Map[String, QueryParam]](Map.empty)(v =>
          Map(key.value.name -> ParameterMapper[V].toQueryParam(v))
        )
      }
  }

  trait LowPriority extends Poly1 {
    implicit def all[K <: Symbol, V: ParameterMapper](implicit key: Witness.Aux[K]): Case.Aux[FieldType[K, V], Map[String, QueryParam]] =
      at[FieldType[K, V]] { (v: V) =>
        Map(key.value.name -> ParameterMapper[V].toQueryParam(v))
      }
  }

}
