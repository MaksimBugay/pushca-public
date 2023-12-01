package bmv.org.pushcaverifier.api;

import static bmv.org.pushcaverifier.client.OpenConnectionFactory.delay;
import static bmv.org.pushcaverifier.processor.TestProcessorType.BINARY_MESSAGE_WITH_ACKNOWLEDGE_WS;
import static bmv.org.pushcaverifier.processor.TestProcessorType.BINARY_MESSAGE_WS;
import static bmv.org.pushcaverifier.processor.TestProcessorType.MESSAGE_WITH_ACKNOWLEDGE_REST;
import static bmv.org.pushcaverifier.processor.TestProcessorType.MESSAGE_WITH_ACKNOWLEDGE_WS;
import static bmv.org.pushcaverifier.processor.TestProcessorType.MESSAGE_WITH_DELIVERY_GUARANTEE_REST;
import static bmv.org.pushcaverifier.processor.TestProcessorType.SIMPLE_MESSAGE_REST;
import static bmv.org.pushcaverifier.processor.TestProcessorType.SIMPLE_MESSAGE_WS;
import static bmv.org.pushcaverifier.util.DateTimeUtility.getCurrentTimestampMs;

import bmv.org.pushcaverifier.client.Channel;
import bmv.org.pushcaverifier.client.ClientWithPool;
import bmv.org.pushcaverifier.client.ClientsPool;
import bmv.org.pushcaverifier.client.MessageService;
import bmv.org.pushcaverifier.client.PClientWithPusherId;
import bmv.org.pushcaverifier.client.rest.PooledHttpWebClient;
import bmv.org.pushcaverifier.config.ConfigService;
import bmv.org.pushcaverifier.model.TestMessage;
import bmv.org.pushcaverifier.processor.StatisticsHolder;
import bmv.org.pushcaverifier.processor.StatisticsHolder.ExecutionResults;
import bmv.org.pushcaverifier.processor.TestProcessorType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class Bombardier {

  public static final Logger LOGGER = LoggerFactory.getLogger(MessageService.class);

  private final ConfigService configService;

  private final ClientsPool clientsPool;

  private final PooledHttpWebClient pooledHttpWebClient;

  private final StatisticsHolder statisticsHolder;

  private final ReactiveRedisOperations<String, ExecutionResults> redisOperations;

  private final Executor asyncExecutor;

  private final Map<TestProcessorType, BiConsumer<ChannelProvider, TestMessage>> sendChannels;

  public Bombardier(ConfigService configService, MessageService messageService,
      ClientsPool clientsPool, PooledHttpWebClient pooledHttpWebClient,
      StatisticsHolder statisticsHolder,
      ReactiveRedisOperations<String, ExecutionResults> redisOperations) {
    this.redisOperations = redisOperations;

    sendChannels = Map.of(
        SIMPLE_MESSAGE_WS,
        (channelProvider, message) -> messageService.sendMessage(
            channelProvider.ws(), message
        ),
        BINARY_MESSAGE_WS,
        (channelProvider, message) -> messageService.sendBinary(
            channelProvider.ws(), message
        ),
        BINARY_MESSAGE_WITH_ACKNOWLEDGE_WS,
        (channelProvider, message) -> messageService.sendBinaryWithAcknowledge(
            channelProvider.ws(), message
        ),
        SIMPLE_MESSAGE_REST,
        (channelProvider, message) -> messageService.sendMessage(
            channelProvider.webClient, message
        ).block(Duration.ofSeconds(configService.getResponseTimeoutMs())),
        MESSAGE_WITH_ACKNOWLEDGE_WS,
        (channelProvider, message) -> messageService.sendMessageWithAcknowledge(
            channelProvider.ws(), message
        ),
        MESSAGE_WITH_ACKNOWLEDGE_REST,
        (channelProvider, message) -> messageService.sendMessageWithAcknowledge(
            channelProvider.webClient, message
        ).block(Duration.ofSeconds(configService.getResponseTimeoutMs())),
        MESSAGE_WITH_DELIVERY_GUARANTEE_REST,
        (channelProvider, message) -> messageService.sendMessageWithDeliveryGuarantee(
            channelProvider.webClient, message
        ).block(Duration.ofSeconds(configService.getResponseTimeoutMs()))
    );

    this.configService = configService;
    this.clientsPool = clientsPool;
    this.pooledHttpWebClient = pooledHttpWebClient;
    this.statisticsHolder = statisticsHolder;

    this.asyncExecutor = createAsyncExecutor(
        "SendMessageExecutor",
        5000
    );

    this.asyncExecutor.execute(this::executeAll);
  }

  private void executeAll() {
    if (configService.getCleanHistory()) {
      ((ReactiveRedisTemplate<String, ExecutionResults>) redisOperations).getConnectionFactory()
          .getReactiveConnection().serverCommands().flushAll().block();
    }
    clientsPool.initPool();
    List<CompletableFuture<Void>> iterations = new ArrayList<>();
    for (int n = 0; n < pooledHttpWebClient.getPoolSize(); n++) {
      int index = n;
      CompletableFuture<Void> iteration = CompletableFuture.runAsync(
          () -> executeIteration(index, pooledHttpWebClient.getWebClient(index)),
          asyncExecutor
      );
      iterations.add(iteration);
    }
    iterations.forEach(CompletableFuture::join);
    statisticsHolder.printIterationStatistic(true);
    statisticsHolder.printStatistic();
  }

  private void executeIteration(int n, WebClient webClient) {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < Duration.ofMinutes(1).toMillis()) {
      final ClientWithPool[] clients = clientsPool.getRandomClients();
      final TestMessage message = createTestMessage(clients[0].getClient(), n);
      try {
        sendMessage(webClient, clients[1], message);
      } catch (Exception ex) {
        LOGGER.error("Failed attempt to send message: {}", message);
      }
      delay(Duration.ofMillis(configService.getLoadTestRepeatIntervalMs()));
    }

    if (MESSAGE_WITH_ACKNOWLEDGE_REST != configService.getTestProcessorType()) {
      delay(Duration.ofMinutes(1));

      statisticsHolder.addDeliveredMessages(
          statisticsHolder.getIssuedMessages().stream()
              .filter(msg -> msg.iteration() == n)
              .map(clientsPool::messageHistoryContainsValidMessage)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet())
      );
    }
  }

  private void sendMessage(WebClient webClient, ClientWithPool wsClient, TestMessage message) {
    statisticsHolder.addIssuedMessage(message);
    final ClientWithPool sender =
        (configService.getTestProcessorType().getChannel() == Channel.ws) ? wsClient : null;
    sendChannels.get(configService.getTestProcessorType()).accept(
        new ChannelProvider(sender, webClient), message
    );
    LOGGER.debug("Message with id {} was issued", message.id());
    if (MESSAGE_WITH_ACKNOWLEDGE_REST == configService.getTestProcessorType()) {
      statisticsHolder.addDeliveredMessage(new TestMessage(
          message.receiver(),
          message.id(),
          message.sendTime(),
          message.accountId(),
          getCurrentTimestampMs(),
          message.preserveOrder(),
          message.iteration()
      ));
    }
  }

  private TestMessage createTestMessage(PClientWithPusherId client, int n) {
    return new TestMessage(
        client,
        UUID.randomUUID().toString(),
        getCurrentTimestampMs(),
        client.accountId(),
        null,
        configService.isPreserveOrder(),
        n
    );
  }

  public static Executor createAsyncExecutor(String threadNamePrefix, int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxPoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(500_000);
    executor.setThreadNamePrefix(threadNamePrefix + "-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }

  public record ChannelProvider(ClientWithPool ws, WebClient webClient) {

  }
}
