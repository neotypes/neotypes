package neotypes

import org.scalatest.Suites

/** Group all the Async specs into one big suite, which can be called for each Async type. */
abstract class AsyncSuite[F[_]](testkit: AsyncTestkit[F])
    extends Suites(
      new AsyncGuaranteeSpec(testkit),
      new AsyncDriverSpec(testkit),
      new AsyncTransactionSpec(testkit),
      new AsyncDriverTransactSpec(testkit),
      new AsyncDriverConcurrentUsageSpec(testkit),
      new AsyncParameterSpec(testkit),
      new AsyncAlgorithmSpec(testkit),
      new cats.data.AsyncCatsDataSpec(testkit)
    )

/** Group all the Stream specs into one big suite, which can be called for each Stream type. */
abstract class StreamSuite[S[_], F[_]](testkit: StreamTestkit[S, F])
    extends Suites(
      new StreamGuaranteeSpec(testkit),
      new StreamDriverSpec(testkit),
      new StreamTransactionSpec(testkit),
      new StreamDriverTransactSpec(testkit),
      new StreamDriverConcurrentUsageSpec(testkit),
      new StreamParameterSpec(testkit),
      new StreamAlgorithmSpec(testkit),
      new cats.data.StreamCatsDataSpec(testkit)
    )
