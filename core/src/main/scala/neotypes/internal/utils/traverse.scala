package neotypes
package internal.utils

import scala.collection.Factory
import scala.collection.mutable.Builder

private[neotypes] object traverse {
  def traverseAs[A, B, C](factory: Factory[B, C])
                         (iter: Iterator[A])
                         (f: A => Either[Throwable, B]): Either[Throwable, C] = {
    @annotation.tailrec
    def loop(acc: Builder[B, C]): Either[Throwable, C] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc.addOne(value))
        case Left(e)      => Left(e)
      } else {
        Right(acc.result())
      }
    loop(acc = factory.newBuilder)
  }
}
