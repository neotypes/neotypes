package neotypes
package generic

import mappers.ResultMapper

import shapeless3.deriving.*

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
        .map[(String, ResultMapper[C])] { (caseName, idx) =>
          caseName -> inst.inject(idx) { [t <: C] => (rm: ResultMapper[t]) =>
            rm.widen[C]
          }
        }
        .toList
