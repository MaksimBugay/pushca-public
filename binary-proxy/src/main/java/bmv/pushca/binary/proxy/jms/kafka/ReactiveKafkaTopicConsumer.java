package bmv.pushca.binary.proxy.jms.kafka;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;

import bmv.pushca.binary.proxy.jms.TopicConsumer;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.module.PayloadProcessorModule;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

public class ReactiveKafkaTopicConsumer<T, P extends PayloadProcessorModule<T>>
    extends TopicConsumer<T, P> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReactiveKafkaTopicConsumer.class.getName());

  private final ReceiverOptions<UUID, String> receiverOptions;

  public ReactiveKafkaTopicConsumer(String bootstrapServers,
      String clientId, String groupId, Class<T> payloadClass,
      String jmsTopicName, P payloadProcessor,
      TopicProducer topicProducer) {
    super(jmsTopicName, payloadClass, payloadProcessor, topicProducer);
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, UUIDDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
    receiverOptions = ReceiverOptions.create(props);
  }

  public Disposable consumeMessages() {

    ReceiverOptions<UUID, String> options =
        receiverOptions.subscription(Collections.singleton(jmsTopicName))
            .addAssignListener(partitions -> LOGGER.debug("onPartitionsAssigned {}", partitions))
            .addRevokeListener(partitions -> LOGGER.debug("onPartitionsRevoked {}", partitions));
    Flux<ReceiverRecord<UUID, String>> kafkaFlux = KafkaReceiver.create(options).receive();
    return kafkaFlux
        .subscribe(record -> {
          ReceiverOffset offset = record.receiverOffset();
          Instant timestamp = Instant.ofEpochMilli(record.timestamp());
          InternalMessage internalMessage = fromJson(record.value(), InternalMessage.class);
          LOGGER.info("Received message: topic-partition={} offset={} timestamp={} key={} value={}",
              offset.topicPartition(),
              offset.offset(),
              timestamp.toString(),
              record.key(),
              record.value());
          processInternalMessage(internalMessage);
          offset.acknowledge();
        });
  }
}
