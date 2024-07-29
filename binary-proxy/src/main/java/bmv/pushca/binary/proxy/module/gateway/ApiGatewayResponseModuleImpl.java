package bmv.pushca.binary.proxy.module.gateway;

import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.internal.api.ApiGatewayResponseModule;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.module.PayloadProcessorModule;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApiGatewayResponseModuleImpl extends PayloadProcessorModule<Object>
    implements ApiGatewayResponseModule {

  private final AsyncLoadingCache<UUID, String> waitingHall;

  @Autowired
  public ApiGatewayResponseModuleImpl(
      MicroserviceWithKafkaConfiguration configuration,
      InstanceInfoProvider instanceInfoProvider,
      TopicProducer topicProducer,
      NewTopic responseTopic) {
    super(configuration, instanceInfoProvider, topicProducer, Object.class, responseTopic.name());

    this.waitingHall =
        Caffeine.newBuilder()
            .expireAfterWrite(configuration.responseTimeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .buildAsync((key, ignored) -> null);

    start();
  }

  @Override
  public CompletableFuture<String> registerResponseFuture(InternalMessage message) {
    CompletableFuture<String> future = new CompletableFuture<>();
    waitingHall.put(message.id, future);
    return future;
  }

  @Override
  public Object prepareResponse(UUID id, Object payload) {
    CompletableFuture<String> future = waitingHall.asMap().get(id);
    if (future != null) {
      future.complete(JsonUtility.toJson(payload));
    }
    return payload;
  }

  public boolean isWaitingHallEmpty() {
    return waitingHall.asMap().isEmpty();
  }

  public Map<Operation, BiFunction<UUID, Object, Object>> supportedOperations() {
    return Map.of(Operation.PREPARE_RESPONSE, this::prepareResponse);
  }
}
