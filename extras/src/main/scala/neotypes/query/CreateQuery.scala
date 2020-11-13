package neotypes.query

import neotypes.implicits.syntax.cypher._
import neotypes.{CaseClassArgMapper, DeferredQuery, DeferredQueryBuilder}
import shapeless.{<:!<, Annotation, Typeable, |∨|}

import scala.annotation.StaticAnnotation

sealed abstract class CreateQuery[A](nodeName: String, paramName: String) {

  // the query type can either be A or Unit
  def query[R: CreateQuery.Mat: |∨|[Unit, A]#λ](value: A)(implicit mapper: CaseClassArgMapper[A]): DeferredQuery[R] =
    CreateQuery.deferred[A](value, nodeName, paramName, CreateQuery.Mat[R].withReturn).query[R]

  def withNodeName(nodeName: String): CreateQuery[A] = new CreateQuery[A](nodeName, paramName) {}

  def withParamName(paramName: String): CreateQuery[A] = new CreateQuery[A](nodeName, paramName) {}
}

object CreateQuery {

  def apply[A](implicit ev: CreateQuery[A]): CreateQuery[A] = ev

  sealed abstract class Mat[A](val withReturn: Boolean)

  object Mat {
    def apply[A](implicit ev: Mat[A]): Mat[A] = ev

    implicit val unitMat: Mat[Unit] =
      new Mat[Unit](false) {}

    implicit def entityMat[A](implicit ev: A <:!< Unit): Mat[A] =  {
      val _ = ev
      new Mat[A](true) {}
    }
  }

  private def deferred[A: CaseClassArgMapper](caseClass: A, nodeName: String, paramName: String, withReturn: Boolean): DeferredQueryBuilder = {
    val returnFragment = if (withReturn) s"RETURN $paramName" else ""

    c"CREATE" + s"($paramName: $nodeName {" + c"$caseClass })" + returnFragment
  }

  def custom[A](nodeName: String, paramName: String): CreateQuery[A] =
    new CreateQuery[A](nodeName, paramName) {}

  /**
    * Creates a default instance of the [[CreateQuery]] using a name of a case class.
    * If you need to use a custom node/param name, see [[annotated]] or [[custom]].
    *
    * Example:
    * {{{
    * import neotypes.generic.auto._
    *
    * final case class UserProps(name: String, age: Int)
    *
    * val user = UserProps("Joan", 20)
    * val unitQuery = CreateQuery[UserProps].query[Unit](user)
    * //CREATE (u: UserProps { name: $p1, age: $p2 } )
    * val returnQuery = CreateQuery[UserProps].query[UserProps](user)
    * //CREATE (u: UserProps { name: $p1, age: $p2 } ) RETURN u
    * }}}
    */
  implicit def mat[A <: Product: Typeable]: CreateQuery[A] =
    new CreateQuery[A](Typeable[A].describe, Typeable[A].describe.head.toLower.toString) {}

  class settings(val nodeName: String, val paramName: String) extends StaticAnnotation

  /**
    * Creates an instance of the [[CreateQuery]] using an annotation over a case class.
    * Make sure the instance is defined explicitly. The best option is:
    * {{{
    * import neotypes.generic.auto._
    *
    * @CreateQuery.settings(nodeName = "User", paramName = "user")
    * final case class UserProps(name: String, age: Int)
    *
    * object UserProps {
    *   implicit val userCreateQuery: CreateQuery[UserProps] = CreateQuery.annotated
    * }
    * }}}
    *
    * Example:
    * {{{
    * import neotypes.generic.auto._
    *
    * @CreateQuery.settings(nodeName = "User", paramName = "user")
    * final case class UserProps(name: String, age: Int)
    *
    * object UserProps {
    *   implicit val userCreateQuery: CreateQuery[UserProps] = CreateQuery.annotated
    * }
    *
    * val user = UserProps("Joan", 20)
    * val unitQuery = CreateQuery[UserProps].query[Unit](user)
    * //CREATE (user: User { name: $p1, age: $p2 } )
    * val returnQuery = CreateQuery[UserProps].query[UserProps](user)
    * //CREATE (user: User { name: $p1, age: $p2 } ) RETURN user
    * }}}
    *
    * @param annotation the annotation over a case class
    */
  def annotated[A <: Product](implicit annotation: Annotation[settings, A]): CreateQuery[A] = {
    val cfg: settings = annotation()

    new CreateQuery[A](cfg.nodeName, cfg.paramName) {}
  }

}
