package neotypes.refined

import neotypes.mappers.{ParameterMapper, ResultMapper}
import neotypes.model.exceptions.IncoercibleException

import eu.timepit.refined.api.{Refined, RefinedType}

object mappers {
  private type RT[T, P] = RefinedType.AuxT[Refined[T, P], T]

  /** Overload of [[refined]] to help implicit inference, don't call explicitly. */
  implicit final def refinedImpl[T, P](implicit
    mapper: ResultMapper[T],
    refined: RT[T, P]
  ): ResultMapper[Refined[T, P]] =
    mapper.emap { t =>
      refined.refine(t).left.map(msg => IncoercibleException(msg))
    }

  private[mappers] final class RefinedPartiallyApplied[R <: Refined[_, _]](private val dummy: Boolean) extends AnyVal {
    def apply[T](
      mapper: ResultMapper[T]
    )(implicit
      @annotation.implicitNotFound("${R} is not a refined type over ${T}")
      refined: RefinedType.AuxT[R, T]
    ): ResultMapper[Refined[T, refined.P]] =
      refinedImpl[T, refined.P](mapper, refined.asInstanceOf[RT[T, refined.P]])
  }

  def refined[R <: Refined[_, _]]: RefinedPartiallyApplied[R] =
    new RefinedPartiallyApplied(dummy = true)

  implicit final def refinedParameterMapper[T, P](implicit
    mapper: ParameterMapper[T]
  ): ParameterMapper[Refined[T, P]] =
    mapper.contramap(_.value)
}
