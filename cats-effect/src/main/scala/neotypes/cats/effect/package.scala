package neotypes
package cats

package object effect {
  final object implicits extends CatsEffect {
    implicit final val IOAsync: Async.Aux[_root_.cats.effect.IO, FResource[_root_.cats.effect.IO]#R] = catsAsync
  }
}
