package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

abstract class BaseAlgorithmIntegrationSpec[F[_]] extends CleaningIntegrationSpec[F]{
  override val container = GenericContainer("neo4j:3.5.3",
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(30))
  ).configure(_.withClasspathResourceMapping("neo4j.conf", "/var/lib/neo4j/conf/", BindMode.READ_ONLY))
    .configure(_.withClasspathResourceMapping("graph-algorithms-algo-3.5.4.0.jar", "/var/lib/neo4j/plugins/", BindMode.READ_ONLY))
}
