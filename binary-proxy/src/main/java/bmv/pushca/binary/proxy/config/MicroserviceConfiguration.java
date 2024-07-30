package bmv.pushca.binary.proxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicroserviceConfiguration {

  @Value("${binary-proxy.response.timeout.ms}")
  public int responseTimeoutMs;

  @Value("${spring.application.name:}")
  public String appName;

  @Value("${binary-proxy.selectors-threads-pool.size:8}")
  public int webServerSelectorsPoolSize;
  @Value("${binary-proxy.workers-threads-pool.size:500}")
  public int webServerWorkersPoolSize;
  @Value("${binary-proxy.delayed-executor-pool.size:100}")
  public int delayedExecutorPoolSize;
}
