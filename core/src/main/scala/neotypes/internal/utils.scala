package neotypes
package internal

import scala.collection.Factory
import scala.collection.mutable.Builder

private[neotypes] object utils {
  /** Used to swallow warnings. */
  @inline
  final def void(as: Any*): Unit = (as, ())._2

  /** Applies a function to all elements of an iterable,
    * accumulating all success or stopping at the first failure.
    */
  def traverseAs[A, B, C, E](factory: Factory[B, C])
                            (iter: Iterator[A])
                            (f: A => Either[E, B]): Either[E, C] = {
    @annotation.tailrec
    def loop(acc: Builder[B, C]): Either[E, C] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc += value)
        case Left(e)      => Left(e)
      } else {
        Right(acc.result())
      }
    loop(acc = factory.newBuilder)
  }
}
