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

  object sequence {
    def sequenceAsList[T](iter: Iterator[Either[Throwable, T]]): Either[Throwable, List[T]] = {
      @annotation.tailrec
      def loop(acc: List[T]): Either[Throwable, List[T]] =
        if (iter.hasNext) iter.next() match {
          case Right(value) => loop(acc = value :: acc)
          case Left(e)      => Left(e)
        } else {
          Right(acc.reverse)
        }
      loop(acc = List.empty)
    }

    def sequenceAsMap[T](iter: Iterator[(String, Either[Throwable, T])]): Either[Throwable, Map[String, T]] = {
      @annotation.tailrec
      def loop(acc: Map[String, T]): Either[Throwable, Map[String, T]] =
        if (iter.hasNext) iter.next() match {
          case (key, Right(value)) => loop(acc = acc + (key -> value))
          case (_,   Left(e))      => Left(e)
        } else {
          Right(acc)
        }
      loop(acc = Map.empty)
    }
  }
}
