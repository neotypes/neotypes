package neotypes

import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.summary.ResultSummary

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

object mappers {
  @implicitNotFound("Could not find the ResultMapper for ${A}")
  trait ResultMapper[A] { self =>
    def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A]

    /**
      * Allows supplying a secondary [[ResultMapper]] to try if the original fails.
      *
      * @param mapper A [[ResultMapper]] to use if the current one fails.
      * @tparam AA A type that is possibly a supertype of your original [[ResultMapper]] type.
      * @return A new [[ResultMapper]] that returns the type of the supplied secondary mapper.
      */
    def or[AA >: A](mapper: => ResultMapper[AA]): ResultMapper[AA] = new ResultMapper[AA] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, AA] = {
        self.to(value, typeHint) match {
          case r @ Right(_) => r
          case Left(_) => mapper.to(value, typeHint)
        }
      }
    }

    /**
      * Creates a new [[ResultMapper]] by applying a function to the result value, if successful.
      *
      * @param f A function to apply to the result value of this ResultMapper.
      * @tparam B The return type of your supplied function.
      * @return A new ResultMapper that applies your function to the result.
      */
    def map[B](f: A => B): ResultMapper[B] = new ResultMapper[B] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] =
        self.to(value, typeHint).right.map(f)
    }

    /**
      * Bind a function over this [[ResultMapper]], if successful.
      * Useful for creating decoders that depend on multiple values in sequence.
      *
      * @param f A function that returns a new [[ResultMapper]].
      * @tparam B The result type of your new [[ResultMapper]] from your function.
      * @return A new [[ResultMapper]] derived from the value your original [[ResultMapper]] outputs.
      */
    def flatMap[B](f: A => ResultMapper[B]): ResultMapper[B] = new ResultMapper[B] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] = self.to(value, typeHint) match {
        case Right(a) => f(a).to(value, typeHint)
        case l @ Left(_) => l.asInstanceOf[Either[Throwable, B]]
      }
    }

    /**
      * Combines the results of this [[ResultMapper]] with another as a tuple pair.
      *
      * @param fa A second [[ResultMapper]] that reads the same input values.
      * @tparam B The type of your second [[ResultMapper]] results.
      * @return A [[ResultMapper]] that produces a pair of values.
      */
    def product[B](fa: ResultMapper[B]): ResultMapper[(A, B)] = new ResultMapper[(A, B)] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, (A, B)] =
        self.flatMap(t => fa.map(a => (t, a))).to(value, typeHint)
    }

    /**
      * Produces a [[ResultMapper]] where either the original or secondary mapper succeeds.
      * The original mapper result is on the Left side, and the secondary mapper is on the Right.
      *
      * @param fa A secondary [[ResultMapper]] to try if the first one fails.
      * @tparam B The result type of your secondary [[ResultMapper]].
      * @return A [[ResultMapper]] that, if sucessful, will return a value of either the original or secondary type.
      */
    def either[B](fa: ResultMapper[B]): ResultMapper[Either[A, B]] = new ResultMapper[Either[A, B]] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Either[A, B]] =
        self.to(value, typeHint) match {
          case Right(r) => Right(Left(r))
          case Left(_) => fa.to(value, typeHint) match {
            case Right(r) => Right(Right(r))
            case l @ Left(_) => l.asInstanceOf[Either[Throwable, Either[A, B]]]
          }
        }
    }
  }

  @implicitNotFound("Could not find the ValueMapper for ${A}")
  trait ValueMapper[A] { self =>
    def to(fieldName: String, value: Option[Value]): Either[Throwable, A]

    /**
      * Allows supplying a secondary [[ValueMapper]] to try if the original fails.
      *
      * @param mapper A [[ValueMapper]] to use if the current one fails.
      * @tparam AA A type that is possibly a supertype of your original [[ValueMapper]] type.
      * @return A new [[ValueMapper]] that returns the type of the supplied secondary mapper.
      */
    def or[AA >: A](mapper: => ValueMapper[AA]): ValueMapper[AA] = new ValueMapper[AA] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, AA] = self.to(fieldName, value) match {
        case r @ Right(_) => r
        case Left(_) => mapper.to(fieldName, value)
      }
    }

    /**
      * Creates a new [[ValueMapper]] by applying a function to the result value, if successful.
      *
      * @param f A function to apply to the result value of this ResultMapper.
      * @tparam B The return type of your supplied function.
      * @return A new ResultMapper that applies your function to the result.
      */
    def map[B](f: A => B): ValueMapper[B] = new ValueMapper[B] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] = self.to(fieldName, value).right.map(f)
    }

    /**
      * Bind a function over this [[ValueMapper]], if successful.
      * Useful for creating decoders that depend on multiple values in sequence.
      *
      * @param f A function that returns a new [[ValueMapper]].
      * @tparam B The result type of your new [[ValueMapper]] from your function.
      * @return A new [[ValueMapper]] derived from the value your original [[ValueMapper]] outputs.
      */
    def flatMap[B](f: A => ValueMapper[B]): ValueMapper[B] = new ValueMapper[B] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] = self.to(fieldName, value) match {
        case Right(a) => f(a).to(fieldName, value)
        case l @ Left(_) => l.asInstanceOf[Either[Throwable, B]]
      }
    }

    /**
      * Combines the results of this [[ValueMapper]] with another as a tuple pair.
      *
      * @param fa A second [[ValueMapper]] that reads the same input values.
      * @tparam B The type of your second [[ValueMapper]] results.
      * @return A [[ValueMapper]] that produces a pair of values.
      */
    def product[B](fa: ValueMapper[B]): ValueMapper[(A, B)] = new ValueMapper[(A, B)] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, (A, B)] =
        self.flatMap(t => fa.map(a => (t, a))).to(fieldName, value)
    }

    /**
      * Produces a [[ValueMapper]] where either the original or secondary mapper succeeds.
      * The original mapper result is on the Left side, and the secondary mapper is on the Right.
      *
      * @param fa A secondary [[ValueMapper]] to try if the first one fails.
      * @tparam B The result type of your secondary [[ValueMapper]].
      * @return A [[ValueMapper]] that, if sucessful, will return a value of either the original or secondary type.
      */
    def either[B](fa: ValueMapper[B]): ValueMapper[Either[A, B]] = new ValueMapper[Either[A, B]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Either[A, B]] =
        self.to(fieldName, value) match {
          case Right(r) => Right(Left(r))
          case Left(_) => fa.to(fieldName, value) match {
            case Right(r) => Right(Right(r))
            case l @ Left(_) => l.asInstanceOf[Either[Throwable, Either[A, B]]]
          }
        }
    }
  }

  @implicitNotFound("Could not find the ExecutionMapper for ${A}")
  trait ExecutionMapper[A] {
    def to(resultSummary: ResultSummary): Either[Throwable, A]
  }

  final case class TypeHint(isTuple: Boolean)

  object TypeHint {
    def apply[A](classTag: ClassTag[A]): TypeHint =
      new TypeHint(classTag.runtimeClass.getName.startsWith("scala.Tuple"))
  }
}
