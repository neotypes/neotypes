package neotypes
package implicits.mappers

import scala.collection.generic._
import scala.collection.{immutable => i, mutable => m}
import scala.{collection => c}
import scala.language.implicitConversions
import scala.language.higherKinds
import scala.reflect.ClassTag

/**
 * This trait contains some implicit conversions that are required
 * to enable the collectAs(Col)(session / tx) method on Scala 2.12.
 * It consists of a group of forwarders to the conversions
 * defined in the scala-collection-compat library.
 * These convert from companion objects of the collections,
 * to instances of CanBuildFrom (Factory).
 *
 * The purpose of this trait, as well of it being mixed in the
 * neotypes.implicits.mappers.results object,
 * is to hide the dependency of the compatibility library to final users.
 * We may as well remove this, and force them to import scala.collection.compat._
 *
 * The original definitions of the conversions can be found here:
 * https://github.com/scala/scala-collection-compat/blob/main/compat/src/main/scala-2.11_2.12/scala/collection/compat/PackageShared.scala
 */
trait CompatibilityMappers {
  implicit final def neotypesGenericCompanionToCBF[A, CC[X] <: c.GenTraversable[X]](fact: GenericCompanion[CC]): CanBuildFrom[Any, A, CC[A]] =
    scala.collection.compat.genericCompanionToCBF(fact)

  implicit final def neotypesSortedSetCompanionToCBF[A: Ordering, CC[X] <: c.SortedSet[X] with c.SortedSetLike[X, CC[X]]](fact: SortedSetFactory[CC]): CanBuildFrom[Any, A, CC[A]] =
    scala.collection.compat.sortedSetCompanionToCBF(fact)

  implicit final def neotypesArrayCompanionToCBF[A: ClassTag](fact: Array.type): CanBuildFrom[Any, A, Array[A]] =
    scala.collection.compat.arrayCompanionToCBF(fact)

  implicit final def neotypesMapFactoryToCBF[K, V, CC[A, B] <: Map[A, B] with c.MapLike[A, B, CC[A, B]]](fact: MapFactory[CC]): CanBuildFrom[Any, (K, V), CC[K, V]] =
    scala.collection.compat.mapFactoryToCBF(fact)

  implicit final def neotypesSortedMapFactoryToCBF[K: Ordering, V, CC[A, B] <: c.SortedMap[A, B] with c.SortedMapLike[A, B, CC[A, B]]](fact: SortedMapFactory[CC]): CanBuildFrom[Any, (K, V), CC[K, V]] =
    scala.collection.compat.sortedMapFactoryToCBF(fact)

  implicit final def neotypesBitSetFactoryToCBF(fact: BitSetFactory[c.BitSet]): CanBuildFrom[Any, Int, c.BitSet] =
    scala.collection.compat.bitSetFactoryToCBF(fact)

  implicit final def neotypesImmutableBitSetFactoryToCBF(fact: BitSetFactory[i.BitSet]): CanBuildFrom[Any, Int, i.BitSet] =
    scala.collection.compat.immutableBitSetFactoryToCBF(fact)

  implicit final def neotypesMutableBitSetFactoryToCBF(fact: BitSetFactory[m.BitSet]): CanBuildFrom[Any, Int, m.BitSet] =
    scala.collection.compat.mutableBitSetFactoryToCBF(fact)
}
