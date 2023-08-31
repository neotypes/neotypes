package neotypes
package generic

import shapeless3.deriving.*
import neotypes.mappers.ResultMapper

trait SealedTraitDerivedCoproductInstances[C] extends ResultMapper.DerivedCoproductInstances[C]
object SealedTraitDerivedCoproductInstances:
  given [C](using
    labelled: Labelling[C],
    inst: K0.CoproductInstances[ResultMapper, C]
  ): SealedTraitDerivedCoproductInstances[C] with
    override def options: List[(String, ResultMapper[C])] =
      labelled
        .elemLabels
        .zipWithIndex
        .map[(String, ResultMapper[C])] { (s, idx) =>
          s -> inst.inject(idx) { [t <: C] => (x: ResultMapper[t]) => x.widen[C] }
        }
        .toList
