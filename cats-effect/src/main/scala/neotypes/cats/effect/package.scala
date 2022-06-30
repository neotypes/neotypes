package neotypes
package cats

import _root_.cats.effect.{IO, Resource}

package object effect {
  type FResource[F[_]] = { type R[A] = Resource[F, A] }

  type IOResource[A] = Resource[IO, A]

  final object implicits extends CatsEffect {
    implicit final val IOAsync: Async.Aux[IO, IOResource] = catsAsync
  }
}
