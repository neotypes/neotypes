package neotypes.monix

package object stream {
  type MonixStream[A] = monix.reactive.Observable[A]

  final object implicits extends MonixStreams
}
