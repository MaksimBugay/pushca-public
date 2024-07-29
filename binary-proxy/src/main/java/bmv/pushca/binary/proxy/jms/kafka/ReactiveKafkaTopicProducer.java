package bmv.pushca.binary.proxy.jms.kafka;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.InternalMessage.SendData;
import bmv.pushca.binary.proxy.module.gateway.ApiGatewayModule;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

@Component
public class ReactiveKafkaTopicProducer implements TopicProducer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReactiveKafkaTopicProducer.class.getName());

  private final KafkaSender<UUID, String> sender;

  @Autowired
  public ReactiveKafkaTopicProducer(
      MicroserviceWithKafkaConfiguration configuration,
      InstanceInfoProvider instanceInfoProvider) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers);
    props.put(ConsumerConfig.CLIENT_ID_CONFIG,
        instanceInfoProvider.getClientId(ApiGatewayModule.class));
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    SenderOptions<UUID, String> senderOptions = SenderOptions.create(props);

    sender = KafkaSender.create(senderOptions);
  }

  @Override
  public CompletableFuture<Void> sendMessages(InternalMessage... messages) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    CountDownLatch latch = new CountDownLatch(messages.length);
    List<SendData> sendDataList = Arrays.stream(messages).flatMap(msg -> msg.getSendData().stream())
        .collect(Collectors.toList());
    sender.send(
        Flux.range(1, sendDataList.size())
            .map(i -> SenderRecord.create(
                new ProducerRecord<>(sendDataList.get(i - 1).topicName(),
                    sendDataList.get(i - 1).internalMessage().id,
                    toJson(sendDataList.get(i - 1).internalMessage())), i)
            )
    )
        .doOnError(e -> {
          LOGGER.error("Cannot send messages to topic " + toJson(messages), e);
          throw new RuntimeException(e);
        })
        .subscribe(r -> {
          RecordMetadata metadata = r.recordMetadata();
          Instant timestamp = Instant.ofEpochMilli(metadata.timestamp());
          LOGGER.info(
              "Message {} sent successfully, topic-partition={}-{} offset={} timestamp={}",
              r.correlationMetadata(),
              metadata.topic(),
              metadata.partition(),
              metadata.offset(),
              timestamp.toString());
          latch.countDown();
          if (latch.getCount() == 0) {
            future.complete(null);
          }
        });
    return future;
  }

  @Override
  public void close() {
    sender.close();
  }
}
