package neotypes
import neotypes.internal.syntax.async._
import neotypes.implicits.syntax.string._

class CancelSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {

  it should "use txFinalizer to rollback on cancel" in executeAsFuture { d =>
    if (testkit == FutureTestkit)
      cancel() //scala Future does not support cancel
    else {
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(d)
        _ <- cancel("CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(d))
        people <- "MATCH (p: PERSON) RETURN p.name".query[String].set(d)
      } yield {
        assert(people == Set("Luis"))
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
