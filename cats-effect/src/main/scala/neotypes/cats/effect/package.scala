package neotypes
package cats

import _root_.cats.effect.{ContextShift, IO}

package object effect {
  final object implicits extends CatsEffect {
    implicit final def IOAsync(implicit cs: ContextShift[IO]): Async.Aux[IO, FResource[IO]#R] = catsAsync
  }
}
