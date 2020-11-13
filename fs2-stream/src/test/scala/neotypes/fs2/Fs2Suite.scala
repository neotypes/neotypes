package neotypes.fs2

import cats.effect.{IO, ContextShift}
import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.cats.effect.IOTestkit
import neotypes.fs2.implicits._
import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for fs2. */
object Fs2Testkit extends StreamTestkit[IO, Fs2IoStream](IOTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val cs: ContextShift[IO] =
        IO.contextShift(ec)

      override def streamToFList[T](stream: Fs2IoStream[T]): IO[List[T]] =
        stream.compile.toList

      override final val streamInstance: Stream.Aux[Fs2IoStream, IO] =
        implicitly
    }
}

/** Execute all the stream specs using fs2. */
final class Fs2Suite extends StreamSuite(Fs2Testkit)
