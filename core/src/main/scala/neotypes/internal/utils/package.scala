package neotypes.internal

package object utils {
  /** Used to swallow unused warnings. */
  @inline
  final def void(as: Any*): Unit = (as, ())._2
}
