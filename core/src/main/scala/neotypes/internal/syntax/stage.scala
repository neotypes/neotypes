package neotypes
package internal.syntax

import java.util.concurrent.{CompletionException, CompletionStage}
import java.util.function.{Consumer => JConsumer, Function => JFunction}

private[neotypes] object stage {
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
