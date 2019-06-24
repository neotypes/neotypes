package neotypes

import java.util.concurrent.{CompletionException, CompletionStage}
import java.util.function.{Consumer => JConsumer, Function => JFunction}

private[neotypes] object utils {
  object stage {
    implicit class CompletionStageOps[T](val underlying: CompletionStage[T]) extends AnyVal {
      def compose[U](f: T => CompletionStage[U]): CompletionStage[U] =
        underlying.thenCompose(
          new JFunction[T, CompletionStage[U]] {
            override def apply(t: T): CompletionStage[U] = f(t)
          }
        )

      def accept(f: T => Unit): CompletionStage[Void] =
        underlying.thenAccept(
          new JConsumer[T] {
            override def accept(t: T): Unit = f(t)
          }
        )
    }

    implicit class VoidCompletionStageOps(val underlying: CompletionStage[Void]) extends AnyVal {
      def recover(fn: Throwable => Unit): CompletionStage[Void] =
        underlying.exceptionally(
          new JFunction[Throwable, Void] {
            override def apply(e: Throwable): Void = {
              // Execute the function.
              e match {
                case _: CompletionException => fn(e.getCause)
                case _                      => fn(e)
              }
              // Return null, which is the only value that conforms to Void.
              // See: https://stackoverflow.com/questions/44171262/convert-scala-unit-to-java-void/44172467#44172467
              None.orNull
            }
          }
        )
    }
  }

  object traverse {
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

    def traverseAsMap[A, B](iter: Iterator[A])
                           (f: A => (String, Either[Throwable, B])): Either[Throwable, Map[String, B]] = {
      @annotation.tailrec
      def loop(acc: Map[String, B]): Either[Throwable, Map[String, B]] =
        if (iter.hasNext) f(iter.next()) match {
          case (key, Right(value)) => loop(acc = acc + (key -> value))
          case (_,   Left(e))      => Left(e)
        } else {
          Right(acc)
        }
      loop(acc = Map.empty)
    }
  }
}
