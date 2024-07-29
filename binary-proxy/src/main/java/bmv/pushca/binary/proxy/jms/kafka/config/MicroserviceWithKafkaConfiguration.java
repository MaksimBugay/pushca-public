package bmv.pushca.binary.proxy.jms.kafka.config;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsRoute;
import bmv.pushca.binary.proxy.model.JmsTopic;
import bmv.pushca.binary.proxy.service.InstanceInfoProvider;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MicroserviceWithKafkaConfiguration extends MicroserviceConfiguration {

  @Value("${spring.kafka.bootstrap-servers}")
  public String bootstrapServers;

  @Value("${spring.kafka.replication.factor:1}")
  private int replicationFactor;

  @Value("${spring.kafka.partition.number:1}")
  private int partitionNumber;

  @Bean
  @Order(-1)
  public List<NewTopic> jmsTopics() {
    return Arrays.stream(JmsTopic.values()).map(
        jmsTopic -> createTopic(jmsTopic.getTopicName()))
        .collect(Collectors.toList());
  }

  @Bean
  @Primary
  public NewTopic responseTopic(InstanceInfoProvider instanceInfoProvider) {
    return createTopic(instanceInfoProvider.getApiGatewayResponseTopicName());
  }

  private NewTopic createTopic(String name) {
    return TopicBuilder.name(name)
        .partitions(partitionNumber)
        .replicas(replicationFactor)
        .compact()
        .build();
  }

  @Bean
  public JmsRoute apiGatewayResponseRoute(InstanceInfoProvider instanceInfoProvider) {
    return new JmsRoute(instanceInfoProvider.getApiGatewayResponseTopicName(),
        Operation.PREPARE_RESPONSE);
  }
}
