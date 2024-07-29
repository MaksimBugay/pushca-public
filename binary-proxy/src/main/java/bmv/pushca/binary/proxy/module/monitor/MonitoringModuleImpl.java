package bmv.pushca.binary.proxy.module.monitor;

import bmv.pushca.binary.proxy.internal.api.MonitoringModule;
import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import bmv.pushca.binary.proxy.model.JmsTopic;
import bmv.pushca.binary.proxy.module.PayloadProcessorModule;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitoringModuleImpl extends PayloadProcessorModule<BrowserProfile> implements
    MonitoringModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringModuleImpl.class);

  private final Map<UUID, BrowserProfile> registry = new ConcurrentHashMap<>();

  @Autowired
  public MonitoringModuleImpl(
      MicroserviceWithKafkaConfiguration configuration,
      InstanceInfoProvider instanceInfoProvider,
      TopicProducer topicProducer) {
    super(configuration, instanceInfoProvider, topicProducer, BrowserProfile.class,
        JmsTopic.MONITOR.getTopicName());
    start();
  }

  public boolean isRegistered(UUID profileId) {
    return registry.get(profileId) != null;
  }

  @Override
  public BrowserProfile registerEvent(UUID id, BrowserProfile browserProfile) {
    LOGGER.info("Profile {}, {} was initialized", browserProfile.uuid(),
        browserProfile.name());
    registry.put(browserProfile.uuid(), browserProfile);
    return browserProfile;
  }

  @Override
  public Map<Operation, BiFunction<UUID, BrowserProfile, BrowserProfile>> supportedOperations() {
    return Map.of(Operation.REGISTER_EVENT, this::registerEvent);
  }
}
