package bmv.pushca.binary.proxy.module.profile;

import bmv.pushca.binary.proxy.internal.api.BrowserProfileModule;
import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.model.JmsTopic;
import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import bmv.pushca.binary.proxy.module.PayloadProcessorModule;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BrowserProfileModuleImpl extends PayloadProcessorModule<BrowserProfile> implements
    BrowserProfileModule {

  private final Map<UUID, BrowserProfile> registry = new ConcurrentHashMap<>();

  @Autowired
  public BrowserProfileModuleImpl(
      MicroserviceWithKafkaConfiguration configuration,
      InstanceInfoProvider instanceInfoProvider,
      TopicProducer topicProducer) {
    super(configuration, instanceInfoProvider, topicProducer, BrowserProfile.class,
        JmsTopic.PROFILE.getTopicName());
    start();
  }

  @Override
  public BrowserProfile get(UUID id, BrowserProfile browserProfile) {
    return registry.get(browserProfile.uuid());
  }

  @Override
  public BrowserProfile init(UUID id, BrowserProfile browserProfile) {
    if (StringUtils.isEmpty(browserProfile.name())) {
      throw new IllegalArgumentException("Name of browser profile cannot be empty");
    }
    String metadata = browserProfile.metaData();
    if (StringUtils.isEmpty(metadata)) {
      try {
        Thread.sleep(6000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      metadata = "Generated metadata";
    }
    BrowserProfile initiatedProfile = new BrowserProfile(
        Long.valueOf(RandomStringUtils.randomNumeric(8)),
        UUID.randomUUID(),
        browserProfile.name(),
        metadata,
        browserProfile.persistent());
    if (Boolean.TRUE.equals(browserProfile.persistent())) {
      registry.put(initiatedProfile.uuid(), initiatedProfile);
    }
    return initiatedProfile;
  }

  @Override
  public Map<Operation, BiFunction<UUID, BrowserProfile, BrowserProfile>> supportedOperations() {
    return Map.of(Operation.INIT_PROFILE, this::init, Operation.GET_PROFILE, this::get);
  }
}
