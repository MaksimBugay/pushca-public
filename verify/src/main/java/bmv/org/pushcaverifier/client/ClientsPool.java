package bmv.org.pushcaverifier.client;

import static bmv.org.pushcaverifier.client.OpenConnectionFactory.delay;

import bmv.org.pushcaverifier.config.ConfigService;
import bmv.org.pushcaverifier.model.TestMessage;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClientsPool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientsPool.class);

  private final ConfigService configService;

  private final Map<PClientWithPusherId, ClientWithPool> registry = new ConcurrentHashMap<>();

  private final AtomicInteger clientIdSeq = new AtomicInteger();

  private final AsyncLoadingCache<Integer, TestMessage> waitingHall;

  private final AsyncLoadingCache<String, Boolean> acknowledgeWaitingHall;

  private final OpenConnectionFactory openConnectionFactory;

  private final Random random = new Random();

  private Rotator rotator;

  private final boolean withAcknowledgeCheck;

  @Autowired
  public ClientsPool(ConfigService configService, OpenConnectionFactory openConnectionFactory) {
    this.configService = configService;
    this.withAcknowledgeCheck = configService.getTestProcessorType().isWithAcknowledgeCheck();
    this.waitingHall = Caffeine.newBuilder()
        .expireAfterWrite(configService.getResponseTimeoutMs() * 2L, TimeUnit.MILLISECONDS)
        .maximumSize(500_000)
        .buildAsync((key, ignored) -> null);
    this.acknowledgeWaitingHall = Caffeine.newBuilder()
        .expireAfterWrite(configService.getResponseTimeoutMs() * 2L, TimeUnit.MILLISECONDS)
        .maximumSize(500_000)
        .buildAsync((key, ignored) -> null);
    this.openConnectionFactory = openConnectionFactory;
  }

  public void initPool() {
    for (int i = 0; i < configService.getNumberOfClients(); i++) {
      PClientWithPusherId client = new PClientWithPusherId(
          "workSpaceMain" + i,
          String.valueOf(clientIdSeq.incrementAndGet()),
          UUID.randomUUID().toString(),
          "MLA_JAVA_HEADLESS"
      );
      int poolSize = configService.getConnectionPoolSize();
      ClientWithPool clientWithPool = new ClientWithPool(
          configService.getPushcaCoordinatorUrl(),
          client,
          poolSize,
          openConnectionFactory
      );
      clientWithPool.init();
      registry.put(client, clientWithPool);
      delay(Duration.ofMillis(1200));
    }
    delay(Duration.ofSeconds(5));
    registry.forEach((client, wsPool) -> {
      long failed = wsPool.getPool().stream().filter(ws -> !ws.isOpen()).count();
      if (failed > 0) {
        LOGGER.error("Cannot open connection pool: client {}", client);
      }
    });
    rotator = new Rotator(registry.size());
  }

  public ClientWithPool[] getRandomClients() {
    List<PClientWithPusherId> keys = new ArrayList<>(registry.keySet());
    //int randomIndex = random.nextInt(keys.size());
    return new ClientWithPool[] {registry.get(keys.get(rotator.getNext())),
        registry.get(keys.get(rotator.getNext()))};
    //int randomIndex = calculateEvenDistributedIndex(keys.size());
    //return new ClientWithPool[] {registry.get(keys.get(randomIndex)),
    //    registry.get(keys.get(randomIndex < (keys.size() - 1) ? randomIndex + 1 : 0))};
  }

  public ClientWithPool getNextClient() {
    List<PClientWithPusherId> keys = new ArrayList<>(registry.keySet());
    int randomIndex = rotator.getNext();
    return registry.get(keys.get(randomIndex));
  }

  public ClientWithPool get(PClientWithPusherId client) {
    return registry.get(client);
  }

  public CompletableFuture<TestMessage> registerResponseFuture(String message) {
    CompletableFuture<TestMessage> future = new CompletableFuture<>();
    waitingHall.put(message.hashCode(), future);
    return future;
  }

  public CompletableFuture<Boolean> registerAcknowledgeFuture(String messageId) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    acknowledgeWaitingHall.put(messageId, future);
    return future;
  }

  public TestMessage messageHistoryContainsValidMessage(TestMessage message) {
    PClientWithPusherId receiver = message.receiver();
    ClientWithPool clientWithPool = registry.get(receiver);
    TestMessage tm = clientWithPool.findValidMessageInHistory(message);
    if (withAcknowledgeCheck) {
      if (messageWasAcknowledged(message)) {
        return tm;
      } else {
        //LOGGER.error("Message was not acknowledged: {}", message);
        return null;
      }
    }
    return tm;
  }

  private boolean messageWasAcknowledged(TestMessage message) {
    final UUID messageId = UUID.fromString(message.id());
    return registry.values().stream()
        .flatMap(cwp -> cwp.getAcknowledgeHistory().keySet().stream()).anyMatch(messageId::equals);
  }
}
