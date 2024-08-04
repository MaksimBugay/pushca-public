package bmv.pushca.binary.proxy.config;

import static bmv.pushca.binary.proxy.pushca.util.NetworkUtils.getInternalIpAddress;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MicroserviceConfiguration {

  private final String instanceId;

  private final String instanceIP;

  @Value("${binary-proxy.response.timeout.ms}")
  public int responseTimeoutMs;

  @Value("${spring.application.name:}")
  public String appName;

  @Value("${binary-proxy.dockerized:true}")
  public boolean dockerized;

  @Value("${binary-proxy.selectors-threads-pool.size:8}")
  public int webServerSelectorsPoolSize;
  @Value("${binary-proxy.workers-threads-pool.size:500}")
  public int webServerWorkersPoolSize;
  @Value("${binary-proxy.websocket-executor-pool.size:100}")
  public int websocketExecutorPoolSize;
  @Value("${binary-proxy.delayed-executor-pool.size:100}")
  public int delayedExecutorPoolSize;

  public MicroserviceConfiguration(@Value("#{environment.SERVICE_INSTANCE_ID}") String instanceId,
      @Value("#{environment.SERVICE_INSTANCE_INTERNAL_IP}") String instanceIP) {
    this.instanceId = getInstanceId(instanceId);
    this.instanceIP = getInstanceIp(instanceIP);
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getInstanceIP() {
    if (StringUtils.hasText(instanceIP)) {
      return instanceIP;
    }
    return "127.0.0.1";
  }

  private String getInstanceId(String instanceId) {
    if (StringUtils.hasText(instanceId)) {
      return instanceId;
    }
    return Optional.ofNullable(System.getenv("HOSTNAME"))
        .orElse(UUID.randomUUID().toString());
  }

  private String getInstanceIp(String ip) {
    if (StringUtils.hasText(ip)) {
      return ip;
    }
    return Optional.ofNullable(getInternalIpAddress()).orElse("127.0.0.1");
  }
}
