package neotypes
package generic

import shapeless3.deriving.*
import neotypes.mappers.ResultMapper

trait SealedTraitDerivedCoproductInstances[C] extends ResultMapper.DerivedCoproductInstances[C]
object SealedTraitDerivedCoproductInstances:
  given [C](using
    labelled: Labelling[C],
    inst: K0.CoproductInstances[ResultMapper, C]
  ): SealedTraitDerivedCoproductInstances[C] =
    new SealedTraitDerivedCoproductInstances[C] {
      override def options: List[(String, ResultMapper[C])] =
        labelled
          .elemLabels
          .zipWithIndex
          .map[(String, ResultMapper[C])] { case (s, idx) =>
            // TODO is there a way to not use asInstanceOf here? widen as it is currently implemented will
            // not suffice
            s -> inst.inject(idx) { [t] => (x: ResultMapper[t]) => x.asInstanceOf[ResultMapper[C]] }
          }
          .toList
    }
