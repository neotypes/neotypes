package neotypes


package object zio {
  private[neotypes] final type ZioResource[A] = _root_.zio.ZIO[_root_.zio.Scope, Throwable, A]

  final object implicits extends Zio
}
