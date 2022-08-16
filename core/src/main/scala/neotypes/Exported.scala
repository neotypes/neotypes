package neotypes

final case class Exported[+T](instance: T) extends AnyVal
