package neotypes
package cats

import _root_.cats.effect.IO

package object effect {
  final object implicits extends CatsEffect {
    implicit final val IOAsync: Async.Aux[IO, FResource[IO]#R] = catsAsync
  }
}
