package bmv.pushca.binary.proxy.config;

import org.springframework.beans.factory.annotation.Value;

public class MicroserviceConfiguration {

  @Value("${api-gateway.response.timeout.ms}")
  public int responseTimeoutMs;

  @Value("${spring.application.name:}")
  public String appName;

  @Value("${api-gateway.requests-threads-pool.size:1200}")
  public int requestThreadsPoolSize;
}
