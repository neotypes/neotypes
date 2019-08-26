package neotypes
package cats

package object effect {
  final object implicits extends CatsEffect {
    implicit final val IOAsync: Async[_root_.cats.effect.IO] = catsAsync
  }
}
