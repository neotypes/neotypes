package neotypes
package internal.syntax

import java.util.concurrent.{CompletionException, CompletionStage}

private[neotypes] object stage {
  implicit class CompletionStageOps[A](private val underlying: CompletionStage[A]) extends AnyVal {
    private final def defaultExHandler[B]: PartialFunction[Throwable, Either[Throwable, B]] = {
      case ex: Throwable => Left(ex)
    }

    private final def acceptImpl[B](cb: Either[Throwable, B] => Unit)
                                   (f: A => Either[Throwable, B])
                                   (g: Throwable => Either[Throwable, B]): CompletionStage[Void] =
      underlying.thenAccept(a => cb(f(a))).exceptionally { ex: Throwable =>
        // Execute the function.
        ex match {
          case _: CompletionException => cb(g(ex.getCause))
          case _                      => cb(g(ex))
        }
        // Return null, which is the only value that conforms to Void.
        // See: https://stackoverflow.com/questions/44171262/convert-scala-unit-to-java-void/44172467#44172467
        None.orNull
      }

    def accept[B](cb: Either[Throwable, B] => Unit)
                 (f: A => Either[Throwable, B]): CompletionStage[Void] =
      acceptImpl(cb)(f)(defaultExHandler)

    def acceptExceptionally[B](cb: Either[Throwable, B] => Unit)
                              (f: A => Either[Throwable, B])
                              (g: PartialFunction[Throwable, Either[Throwable, B]]): CompletionStage[Void] =
      acceptImpl(cb)(f)(g.orElse(defaultExHandler))

    def acceptVoid(cb: Either[Throwable, Unit] => Unit)
                  (implicit ev: A =:= Void): CompletionStage[Void] =
      acceptImpl(cb)(_ => Right(()))(defaultExHandler)
  }
}
