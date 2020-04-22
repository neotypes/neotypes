package neotypes

import exceptions.{PropertyNotFoundException, IncoercibleException}
import types.QueryParam

import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.exceptions.value.Uncoercible
import org.neo4j.driver.v1.summary.ResultSummary

import scala.reflect.ClassTag
import scala.util.Try

object mappers {
  @annotation.implicitNotFound("Could not find the ResultMapper for ${A}")
  trait ResultMapper[A] { self =>
    def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A]

    /**
      * Allows supplying a secondary [[ResultMapper]] to try if the original fails.
      *
      * @param mapper A [[ResultMapper]] to use if the current one fails.
      * @tparam AA A type that is possibly a supertype of your original [[ResultMapper]] type.
      * @return A new [[ResultMapper]] that returns the type of the supplied secondary mapper.
      */
    def or[AA >: A](mapper: => ResultMapper[AA]): ResultMapper[AA] = new ResultMapper[AA] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, AA] = {
        self.to(value, typeHint).left.flatMap(_ => mapper.to(value, typeHint))
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
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] =
        self.to(value, typeHint).map(f)
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
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] =
        self.to(value, typeHint).flatMap(a => f(a).to(value, typeHint))
    }

    /**
      * Combines the results of this [[ResultMapper]] with another as a tuple pair.
      *
      * @param fa A second [[ResultMapper]] that reads the same input values.
      * @tparam B The type of your second [[ResultMapper]] results.
      * @return A [[ResultMapper]] that produces a pair of values.
      */
    def product[B](fa: ResultMapper[B]): ResultMapper[(A, B)] = new ResultMapper[(A, B)] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, (A, B)] =
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
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Either[A, B]] =
        self.to(value, typeHint) match {
          case Right(r) => Right(Left(r))
          case Left(_) => fa.to(value, typeHint) match {
            case Right(r) => Right(Right(r))
            case Left(e) => Left(e)
          }
        }
    }
  }

  object ResultMapper {
    /**
      * Summons an implicit [[ResultMapper]] already in scope by result type.
      *
      * @param mapper A [[ResultMapper]] in scope of the desired type.
      * @tparam A The result type of the mapper.
      * @return A [[ResultMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: ResultMapper[A]): ResultMapper[A] = mapper

    /**
      * Constructs a [[ResultMapper]] that always returns a constant result value.
      *
      * @param a The value to always return.
      * @tparam A The type of the result value.
      * @return A [[ResultMapper]] that always returns the supplied value and never errors.
      */
    def const[A](a: A): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = Right(a)
    }

    /**
      * Constructs a [[ResultMapper]] from a function that parses the results of a Neo4j query.
      *
      * The supplied function takes a sequence of String/Value pairs in the order they are returned from the query per-row.
      * It also takes a TypeHint to indicate whether or not the values are a tuple, if relevant.
      *
      * @param f A function that parses a list of returned field names/values and a supplied [[TypeHint]].
      * @tparam A The result type of this [[ResultMapper]]
      * @return A new [[ResultMapper]] that parses query results with the supplied function.
      */
    def instance[A](f: (List[(String, Value)], Option[TypeHint]) => Either[Throwable, A]): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = f(value, typeHint)
    }

    /**
      * Constructs a [[ResultMapper]] that always returns the specified Throwable.
      *
      * @param failure A throwable error.
      * @tparam A The result type (never returned) of this [[ResultMapper]]
      * @return A [[ResultMapper]] that always returns a throwable error.
      */
    def failed[A](failure: Throwable): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = Left(failure)
    }

    /**
      * Constructs a [[ResultMapper]] from a [[ValueMapper]].
      *
      * @tparam A the type of both the [[ResultMapper]] and the [[ValueMapper]].
      * @return A [[ResultMapper]] that delegates its behaviour to a [[ValueMapper]].
      */
    def fromValueMapper[A](implicit marshallable: ValueMapper[A]): ResultMapper[A] =
      new ResultMapper[A] {
        override def to(fields: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] =
          fields
            .headOption
            .fold(ifEmpty = marshallable.to("", None)) {
              case (name, value) => marshallable.to(name, Some(value))
            }
      }
  }

  @annotation.implicitNotFound("Could not find the ValueMapper for ${A}")
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
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, AA] =
        self.to(fieldName, value).left.flatMap(_ => mapper.to(fieldName, value))
    }

    /**
      * Creates a new [[ValueMapper]] by applying a function to the result value, if successful.
      *
      * @param f A function to apply to the result value of this ResultMapper.
      * @tparam B The return type of your supplied function.
      * @return A new ResultMapper that applies your function to the result.
      */
    def map[B](f: A => B): ValueMapper[B] = new ValueMapper[B] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] =
        self.to(fieldName, value).map(f)
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
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] =
        self.to(fieldName, value).flatMap(a => f(a).to(fieldName, value))
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
            case Left(e) => Left(e)
          }
        }
    }
  }

  object ValueMapper {
    /**
      * Summons an implicit [[ValueMapper]] already in scope by result type.
      *
      * @param mapper A [[ValueMapper]] in scope of the desired type.
      * @tparam A The result type of the mapper.
      * @return A [[ValueMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: ValueMapper[A]): ValueMapper[A] = mapper

    /**
      * Constructs a [[ValueMapper]] that always returns a constant result value.
      *
      * @param a The value to always return.
      * @tparam A The type of the result value.
      * @return A [[ValueMapper]] that always returns the supplied value and never errors.
      */
    def const[A](a: A): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = Right(a)
    }

    /**
      * Constructs a [[ValueMapper]] from a function that parses the results of a Neo4j query.
      *
      * The supplied function takes a pair containing the field name and an optional value.
      *
      * @param f A function that parses a list of returned field names/values and a supplied [[TypeHint]].
      * @tparam A The result type of this [[ValueMapper]]
      * @return A new [[ValueMapper]] that parses query results with the supplied function.
      */
    def instance[A](f: (String, Option[Value]) => Either[Throwable, A]): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = f(fieldName, value)
    }

    /**
      * Constructs a [[ValueMapper]] that always returns the specified Throwable.
      *
      * @param failure A throwable error.
      * @tparam A The result type (never returned) of this [[ValueMapper]]
      * @return A [[ValueMapper]] that always returns a throwable error.
      */
    def failed[A](failure: Throwable): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = Left(failure)
    }

    /**
      * Constructs a [[ValueMapper]] from a cast function.
      *
      * @param f The cast function.
      * @tparam A The output type of the cast function.
      * @return a [[ValueMapper]] that will cast its outputs using the provided function.
      */
    def fromCast[A](f: Value => A): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] =
        value match {
          case Some(v) =>
            Try(f(v)).toEither.left.map {
              case ex: Uncoercible => IncoercibleException(s"${ex.getLocalizedMessage} for field [$fieldName] with value [${value}]", ex)
              case ex: Throwable => ex
            }

          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))
        }
    }
  }

  @annotation.implicitNotFound("Could not find the ExecutionMapper for ${A}")
  trait ExecutionMapper[A] {
    def to(resultSummary: ResultSummary): Either[Throwable, A]
  }

  final case class TypeHint(isTuple: Boolean)

  object TypeHint {
    def apply[A](classTag: ClassTag[A]): TypeHint =
      new TypeHint(classTag.runtimeClass.getName.startsWith("scala.Tuple"))
  }

  @annotation.implicitNotFound("Could not find the ParameterMapper for ${A}")
  sealed trait ParameterMapper[A] { self =>
    /**
      * Casts a Scala value of type A into a valid Neo4j parameter.
      *
      * @param scalaValue The value to cast.
      * @tparam A The type of the scalaValue.
      * @return The same value casted as a valid Neo4j parameter.
      */
    def toQueryParam(scalaValue: A): QueryParam

    /**
      * Creates a new [[ParameterMapper]] by applying a function
      * to a Scala value of type B before casting it using this mapper.
      *
      * @param f The function to apply before the cast.
      * @tparam B The input type of the supplied function.
      * @return A new [[ParameterMapper]] for values of type B.
      */
    final def contramap[B](f: B => A): ParameterMapper[B] = new ParameterMapper[B] {
      override def toQueryParam(scalaValue: B): QueryParam =
        self.toQueryParam(f(scalaValue))
    }
  }

  object ParameterMapper {
    /**
      * Summons an implicit [[ParameterMapper]] already in scope by result type.
      *
      * @param mapper A [[ParameterMapper]] in scope of the desired type.
      * @tparam A The input type of the mapper.
      * @return A [[ParameterMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: ParameterMapper[A]): ParameterMapper[A] = mapper

    /**
      * Constructs a [[ParameterMapper]] that always returns a constant value.
      *
      * @param v The value to always return.
      * @tparam A The type of the input value.
      * @return A [[ParameterMapper]] that always returns the supplied value.
      */
    def const[A](v: AnyRef): ParameterMapper[A] = new ParameterMapper[A] {
      override def toQueryParam(scalaValue: A): QueryParam =
        new QueryParam(v)
    }

    /**
      * Constructs a [[ParameterMapper]] from a cast function.
      *
      * @param f The cast function.
      * @tparam A The input type of the cast function.
      * @return a [[ParameterMapper]] that will cast its inputs using the provided function.
      */
    private[neotypes] def fromCast[A](f: A => AnyRef): ParameterMapper[A] = new ParameterMapper[A] {
      override def toQueryParam(scalaValue: A): QueryParam =
        new QueryParam(f(scalaValue))
    }

    /**
      * Constructs a [[ParameterMapper]] that works like an identity function.
      *
      * Many values do not require any mapping to be used as parameters.
      * For those cases, this private helper is useful to reduce boilerplate.
      *
      * @tparam A The type of the input value (must be a subtype of [[AnyRef]]).
      * @return A [[ParameterMapper]] that always returns its input unchanged.
      */
    private[neotypes] def identity[A <: AnyRef] = new ParameterMapper[A] {
      override def toQueryParam(scalaValue: A): QueryParam =
        new QueryParam(scalaValue)
    }
  }
}
