package neotypes
package mappers

import model.exceptions.KeyMapperException

@annotation.implicitNotFound("${A} is not a valid type for keys")
trait KeyMapper[A] { self =>
  /**
    * Encodes a value as a key.
    *
    * @param a The value to encode.
    * @tparam A The type of the value to encode.
    * @return The key corresponding to that value.
    */
  def encodeKey(a: A): String

  /**
    * Decodes a key as a value.
    *
    * @param key The key to decode.
    * @tparam A The type of the value to decode.
    * @return The value corresponding to that key or an error.
    */
  def decodeKey(key: String): Either[KeyMapperException, A]

  /**
    * Creates a new [[KeyMapper]] by providing
    * transformation functions to and from A.
    *
    * @param f The function to apply before the encoding.
    * @param g The function to apply after the decoding.
    * @tparam B The type of the new [[KeyMapper]]
    * @return A new [[KeyMapper]] for values of type B.
    */
  final def imap[B](f: B => A)(g: A => Either[KeyMapperException, B]): KeyMapper[B] = new KeyMapper[B] {
    override def encodeKey(b: B): String =
      self.encodeKey(f(b))

    override def decodeKey(key: String): Either[KeyMapperException, B] =
      self.decodeKey(key).flatMap(g)
  }
}

object KeyMapper {
  /**
    * Summons an implicit [[KeyMapper]] already in scope by result type.
    *
    * @param mapper A [[KeyMapper]] in scope of the desired type.
    * @tparam A The result type of the mapper.
    * @return A [[KeyMapper]] for the given type currently in implicit scope.
    */
  def apply[A](implicit mapper: KeyMapper[A]): KeyMapper[A] = mapper

  implicit final val string: KeyMapper[String] = new KeyMapper[String] {
    override def encodeKey(key: String): String =
      key

    override def decodeKey(key: String): Either[KeyMapperException, String] =
      Right(key)
  }
}
