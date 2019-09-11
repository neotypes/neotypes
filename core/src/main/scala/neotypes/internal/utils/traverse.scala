package neotypes
package internal.utils

import scala.collection.compat._
import scala.collection.compat.Factory
import scala.collection.mutable.Builder

private[neotypes] object traverse {
  final def traverseAs[A, B, C](factory: Factory[B, C])
                               (iter: Iterator[A])
                               (f: A => Either[Throwable, B]): Either[Throwable, C] = {
    @annotation.tailrec
    def loop(acc: Builder[B, C]): Either[Throwable, C] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc += value)
        case Left(e)      => Left(e)
      } else {
        Right(acc.result())
      }
    loop(acc = factory.newBuilder)
  }

  final def traverseAsList[A, B](iter: Iterator[A])
                                (f: A => Either[Throwable, B]): Either[Throwable, List[B]] =
    traverseAs(List : Factory[B, List[B]])(iter)(f)

  final def traverseAsSet[A, B](iter: Iterator[A])
                               (f: A => Either[Throwable, B]): Either[Throwable, Set[B]] =
    traverseAs(Set : Factory[B, Set[B]])(iter)(f)

  final def traverseAsVector[A, B](iter: Iterator[A])
                                  (f: A => Either[Throwable, B]): Either[Throwable, Vector[B]] =
    traverseAs(Vector : Factory[B, Vector[B]])(iter)(f)

  final def traverseAsMap[A, K, V](iter: Iterator[A])
                                  (f: A => Either[Throwable, (K, V)]): Either[Throwable, Map[K, V]] =
    traverseAs(Map : Factory[(K, V), Map[K, V]])(iter)(f)
}
