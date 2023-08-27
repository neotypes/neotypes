package neotypes

import org.scalatest.{EitherValues, Inside, Inspectors, LoneElement, OptionValues}
import org.scalatest.flatspec.{AnyFlatSpec, AsyncFlatSpec}
import org.scalatest.matchers.should.Matchers

/** Common ScalaTest mixins we will use in all suites. */
sealed trait CommonMixins
    extends Matchers
    with EitherValues
    with Inside
    with Inspectors
    with LoneElement
    with OptionValues

/** Base class for all synchronous specs. */
abstract class BaseSynchronousSpec extends AnyFlatSpec with CommonMixins

/** Base class for all asynchronous specs. */
abstract class BaseAsynchronousSpec extends AsyncFlatSpec with CommonMixins
