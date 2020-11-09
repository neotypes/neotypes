package neotypes.generic

final case class Exported[+T](instance: T) extends AnyVal
