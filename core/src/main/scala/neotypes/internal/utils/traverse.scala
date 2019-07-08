package neotypes
package internal.utils

private[neotypes] object traverse {
  def traverseAsList[A, B](iter: Iterator[A])
                          (f: A => Either[Throwable, B]): Either[Throwable, List[B]] = {
    @annotation.tailrec
    def loop(acc: List[B]): Either[Throwable, List[B]] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = value :: acc)
        case Left(e)      => Left(e)
      } else {
        Right(acc.reverse)
      }
    loop(acc = List.empty)
  }

  def traverseAsSet[A, B](iter: Iterator[A])
                          (f: A => Either[Throwable, B]): Either[Throwable, Set[B]] = {
    @annotation.tailrec
    def loop(acc: Set[B]): Either[Throwable, Set[B]] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc + value)
        case Left(e)      => Left(e)
      } else {
        Right(acc)
      }
    loop(acc = Set.empty)
  }

  def traverseAsVector[A, B](iter: Iterator[A])
                            (f: A => Either[Throwable, B]): Either[Throwable, Vector[B]] = {
    @annotation.tailrec
    def loop(acc: Vector[B]): Either[Throwable, Vector[B]] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc :+ value)
        case Left(e)      => Left(e)
      } else {
        Right(acc)
      }
    loop(acc = Vector.empty)
  }

  def traverseAsMap[A, K, V](iter: Iterator[A])
                            (f: A => Either[Throwable, (K, V)]): Either[Throwable, Map[K, V]] = {
    @annotation.tailrec
    def loop(acc: Map[K, V]): Either[Throwable, Map[K, V]] =
      if (iter.hasNext) f(iter.next()) match {
        case Right((key, value)) => loop(acc = acc + (key -> value))
        case Left(e)             => Left(e)
      } else {
        Right(acc)
      }
    loop(acc = Map.empty)
  }
}
