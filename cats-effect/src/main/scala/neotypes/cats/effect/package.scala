package neotypes.cats

import cats.effect.{IO, Resource}

package object effect {
  type FResource[F[_]] = { type R[A] = Resource[F, A] }

  type IOResource[A] = Resource[IO, A]

  object implicits extends CatsEffect {
    implicit final val IOAsync: neotypes.Async.Aux[IO, IOResource] = catsAsync
  }
}
