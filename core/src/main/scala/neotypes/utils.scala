package neotypes.utils

import java.util.concurrent.CompletionException
import java.util.function.{Consumer => JConsumer, Function => JFunction}

private[neotypes] object FunctionUtils {
  implicit def function[T, U](f: Function[T, U]): JFunction[T, U] =
    new JFunction[T, U] {
      override def apply(t: T): U = f(t)
    }

  implicit def consumer[T](f: Function[T, Unit]): JConsumer[T] =
    new JConsumer[T] {
      override def accept(t: T): Unit = f(t)
    }

  implicit def exceptionally(fn: Throwable => Unit): JFunction[Throwable, Void] =
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
}
