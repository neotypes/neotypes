package neotypes.generic

import neotypes.mappers.{ResultMapper, TypeHint}

import org.neo4j.driver.Value
import shapeless.{HList, LabelledGeneric, Lazy}

import scala.reflect.ClassTag

trait DerivedResultMapper[A] extends ResultMapper[A]

object DerivedResultMapper {

  implicit final def deriveResultMapper[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                                 reprDecoder: Lazy[ReprResultMapper[R]],
                                                 ct: ClassTag[A]): DerivedResultMapper[A] =
    new DerivedResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] =
        reprDecoder.value.to(value, Some(TypeHint(ct))).map(gen.from)
    }

}
