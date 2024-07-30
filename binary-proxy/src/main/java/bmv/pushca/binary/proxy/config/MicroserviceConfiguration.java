package bmv.pushca.binary.proxy.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MicroserviceConfiguration {

  private final String instanceId;

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

  public MicroserviceConfiguration(@Value("#{environment.SERVICE_INSTANCE_ID}") String instanceId) {
    this.instanceId = getInstanceId(instanceId);
  }

  public String getInstanceId() {
    return instanceId;
  }

  private String getInstanceId(String instanceId) {
    if (StringUtils.hasText(instanceId)) {
      return instanceId;
    }
    return Optional.ofNullable(System.getenv("HOSTNAME"))
        .orElse(UUID.randomUUID().toString());
  }
}
