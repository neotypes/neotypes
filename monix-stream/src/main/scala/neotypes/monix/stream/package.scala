package neotypes.monix

import monix.reactive.Observable

package object stream {
  type MonixStream[A] = Observable[A]

  final object implicits extends MonixStreams
}
