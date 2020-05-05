package neotypes
package refined

import exceptions.{PropertyNotFoundException, IncoercibleException}
import mappers.{ParameterMapper, ResultMapper, TypeHint, ValueMapper}

import eu.timepit.refined.api.{Refined, RefinedType}
import org.neo4j.driver.v1.Value

trait RefinedMappers {
  implicit final def refinedValueMapper[T, P](implicit mapper: ValueMapper[T], rt: RefinedType.AuxT[Refined[T, P], T]): ValueMapper[Refined[T, P]] =
    new ValueMapper[Refined[T, P]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Refined[T, P]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            mapper
              .to(fieldName, Some(value))
              .flatMap(t => rt.refine(t).left.map(msg => IncoercibleException(msg, None.orNull)))
        }
    }

  implicit final def refinedResultMapper[T, P](implicit marshallable: ValueMapper[Refined[T, P]]): ResultMapper[Refined[T, P]] =
    new ResultMapper[Refined[T, P]] {
      override def to(fields: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable] = Nil): Either[Throwable, Refined[T, P]] =
        fields
          .headOption
          .fold(ifEmpty = marshallable.to("", None)) {
            case (name, value) => marshallable.to(name, Some(value))
          }
    }

  implicit final def RefinedParameterMapper[T, P](implicit mapper: ParameterMapper[T]): ParameterMapper[Refined[T, P]] =
    mapper.contramap(refinedValue => refinedValue.value)
}
