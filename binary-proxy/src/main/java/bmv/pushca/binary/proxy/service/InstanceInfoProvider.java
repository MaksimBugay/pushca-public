package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.model.JmsTopic;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InstanceInfoProvider {

  private final MicroserviceConfiguration configuration;

  private final UUID instanceId = UUID.randomUUID();

  public InstanceInfoProvider(
      MicroserviceConfiguration configuration) {
    this.configuration = configuration;
  }

  public UUID getInstanceId() {
    return instanceId;
  }

  public String getAppName() {
    return configuration.appName;
  }

  public String getClientId(Class<?> serviceClass) {
    return String
        .join("_", configuration.appName, serviceClass.getSimpleName(), instanceId.toString());
  }

  public String getGroupId(Class<?> serviceClass) {
    return String.join("_", configuration.appName, serviceClass.getSimpleName());
  }

  public String getApiGatewayResponseTopicName() {
    return String.join(".", JmsTopic.RESPONSE.getTopicName(), instanceId.toString());
  }
}
