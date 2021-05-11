package neotypes.fs2

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.cats.effect.IOTestkit
import neotypes.fs2.implicits._

import cats.effect.IO

import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for fs2. */
object Fs2Testkit extends StreamTestkit[Fs2IoStream, IO](IOTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override def streamToFList[A](stream: Fs2IoStream[A]): IO[List[A]] =
        stream.compile.toList

      override final val streamInstance: Stream.Aux[Fs2IoStream, IO] =
        implicitly
    }
}

/** Execute all the stream specs using fs2. */
final class Fs2Suite extends StreamSuite(Fs2Testkit)
