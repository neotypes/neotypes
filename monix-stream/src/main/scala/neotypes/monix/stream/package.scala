package neotypes.monix

package object stream {
  type MonixStream[A] = monix.reactive.Observable[A]

  object implicits extends MonixStreams
}
