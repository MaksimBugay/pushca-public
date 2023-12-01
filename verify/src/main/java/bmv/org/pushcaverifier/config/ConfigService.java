package bmv.org.pushcaverifier.config;

import bmv.org.pushcaverifier.processor.TestProcessorType;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConfigService {

  private final Environment environment;

  @Value("#{environment.PUSHVERIFIER_COORDINATOR_BASE_URL}")
  private String pushcaCoordinatorUrlEnv;

  @Value("#{environment.PUSHVERIFIER_NUMBER_OF_CLIENTS}")
  private Integer numberOfClientsEnv;

  @Value("#{environment.PUSHVERIFIER_LT_REPEAT_INTERVAL_MS}")
  private Integer loadTestRepeatIntervalMsEnv;

  @Value("#{environment.PUSHVERIFIER_TEST_PROCESSOR_TYPE}")
  private TestProcessorType testProcessorTypeEnv;

  @Value("#{environment.PUSHVERIFIER_LT_RUNNER_NUMBER}")
  private Integer loadTestRunnerNumberEnv;

  @Value("#{environment.PUSHVERIFIER_CLIENT_POOL_SIZE}")
  private Integer connectionPoolSizeEnv;

  @Value("#{environment.PUSHVERIFIER_CLEAN_HISTORY}")
  private Boolean cleanHistoryEnv;

  @Value("${verification.load-test.repeat-interval-ms:2}")
  private int loadTestRepeatIntervalMs;

  @Value("${verification.pushca-coordinator.url:}")
  private String pushcaCoordinatorUrl;

  @Value("${verification.processor-type:${verification.processor-type:SIMPLE_MESSAGE_WS}}")
  private TestProcessorType testProcessorType;

  @Value("${verification.number-of-clients:100}")
  private int numberOfClients;

  @Value("${verification.response.timeout.ms:300000}")
  private int responseTimeoutMs;

  @Value("${verification.load-test-runner.number:1}")
  private int loadTestRunnerNumber;

  @Value("${verification.client-connection-pool.size:1}")
  private int connectionPoolSize;

  @Value("${verification.preserve-order:false}")
  private boolean preserveOrder;

  public ConfigService(Environment environment) {
    this.environment = environment;
  }

  public String getPushcaCoordinatorUrl() {
    if (StringUtils.hasText(pushcaCoordinatorUrlEnv)) {
      return pushcaCoordinatorUrlEnv;
    }
    return pushcaCoordinatorUrl;
  }

  public int getNumberOfClients() {
    return Optional.ofNullable(numberOfClientsEnv).orElse(numberOfClients);
  }

  public int getResponseTimeoutMs() {
    return responseTimeoutMs;
  }

  public int getLoadTestRepeatIntervalMs() {
    if (loadTestRepeatIntervalMsEnv != null) {
      return loadTestRepeatIntervalMsEnv;
    }
    return loadTestRepeatIntervalMs;
  }

  public TestProcessorType getTestProcessorType() {
    return Optional.ofNullable(testProcessorTypeEnv).orElse(testProcessorType);
  }

  public int getLoadTestRunnerNumber() {
    return Optional.ofNullable(loadTestRunnerNumberEnv).orElse(loadTestRunnerNumber);
  }

  public int getConnectionPoolSize() {
    return Optional.ofNullable(connectionPoolSizeEnv).orElse(connectionPoolSize);
  }

  public boolean isPreserveOrder() {
    return preserveOrder;
  }

  public String getRedisHost() {
    return environment.getProperty("PUSHVERIFIER_REDIS_HOST");
  }

  public Integer getRedisPort() {
    return Optional.ofNullable(environment.getProperty("PUSHVERIFIER_REDIS_PORT"))
        .map(Integer::parseInt)
        .orElse(null);
  }

  public boolean getCleanHistory() {
    return Boolean.TRUE.equals(cleanHistoryEnv);
  }
}
