package bmv.pushca.binary.proxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicroserviceConfiguration {

  @Value("${api-gateway.response.timeout.ms}")
  public int responseTimeoutMs;

  @Value("${spring.application.name:}")
  public String appName;

  @Value("${api-gateway.requests-threads-pool.size:1200}")
  public int requestThreadsPoolSize;

  @Value("${binary-proxy.delayed-executor.pool-size:100}")
  public int delayedExecutorPoolSize;
}
