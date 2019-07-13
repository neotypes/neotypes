package neotypes.monix

package object stream {
  type MonixStream[T] = monix.reactive.Observable[T]

  final object implicits extends MonixStreams
}
