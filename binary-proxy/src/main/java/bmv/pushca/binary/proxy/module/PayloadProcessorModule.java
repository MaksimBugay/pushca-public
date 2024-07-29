package bmv.pushca.binary.proxy.module;

import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.jms.TopicConsumer;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.jms.kafka.ReactiveKafkaTopicConsumer;
import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.Disposable;

public abstract class PayloadProcessorModule<T> implements DisposableBean {

  private final TopicConsumer<T, PayloadProcessorModule<T>> topicConsumer;
  private Disposable topicConsumerDisposable;

  public PayloadProcessorModule(
      MicroserviceWithKafkaConfiguration configuration,
      InstanceInfoProvider instanceInfoProvider,
      TopicProducer topicProducer,
      Class<T> payloadClass,
      String topicName) {
    topicConsumer = new ReactiveKafkaTopicConsumer<>(
        configuration.bootstrapServers,
        instanceInfoProvider.getClientId(this.getClass()),
        instanceInfoProvider.getGroupId(this.getClass()),
        payloadClass,
        topicName,
        this,
        topicProducer
    );
  }

  protected void start() {
    topicConsumerDisposable = topicConsumer.consumeMessages();
  }

  public abstract Map<Operation, BiFunction<UUID, T, T>> supportedOperations();

  @Override
  public void destroy() {
    if (topicConsumerDisposable != null) {
      topicConsumerDisposable.dispose();
    }
  }
}
