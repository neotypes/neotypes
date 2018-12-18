package neotypes.utils

object FunctionUtils {
  implicit def function[T, U](f: Function[T, U]): java.util.function.Function[T, U] = new java.util.function.Function[T, U] {
    override def apply(t: T): U = f(t)
  }

  implicit def consumer[T](f: Function[T, Unit]): java.util.function.Consumer[T] = new java.util.function.Consumer[T] {
    override def accept(t: T): Unit = f(t)
  }

}
