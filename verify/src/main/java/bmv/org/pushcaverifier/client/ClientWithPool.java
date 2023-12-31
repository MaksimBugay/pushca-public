package bmv.org.pushcaverifier.client;

import static bmv.org.pushcaverifier.util.DateTimeUtility.getCurrentTimestampMs;

import bmv.org.pushcaverifier.model.TestMessage;
import bmv.org.pushcaverifier.pushca.Command;
import bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService;
import bmv.org.pushcaverifier.util.BmvObjectUtils;
import bmv.org.pushcaverifier.util.JsonUtility;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientWithPool {

  private static final Logger LOGGER = LoggerFactory.getLogger("PushServerClient");

  private final Map<UUID, TestMessage> messageHistory = new ConcurrentHashMap<>();

  private final Map<UUID, Long> acknowledgeHistory = new ConcurrentHashMap<>();

  private final Map<Integer, byte[]> binaryHistory = new ConcurrentHashMap<>();

  private final AtomicLong brokenConnectionsCounter = new AtomicLong();

  private final PClientWithPusherId client;

  private final int poolSize;

  private List<SimpleClient> pool = new ArrayList<>();

  private Rotator rotator;

  private final OpenConnectionFactory openConnectionFactory;

  private final String pushcaBaseUrl;

  private final BiConsumer<String, String> messageConsumer;
  private final BiConsumer<SimpleClient, ByteBuffer> dataConsumer;
  private final BiConsumer<Integer, String> afterCloseListener;

  public static String buildId(UUID binaryId, int order) {
    return MessageFormat.format("{0}-{1}", binaryId.toString(), String.valueOf(order));
  }

  public ClientWithPool(String pushcaBaseUrl, PClientWithPusherId client, int poolSize,
      OpenConnectionFactory openConnectionFactory) {
    this.client = client;
    this.poolSize = poolSize;
    this.pushcaBaseUrl = pushcaBaseUrl;
    this.openConnectionFactory = openConnectionFactory;

    this.messageConsumer = (clientId, message) -> {
      if (!clientId.equals(this.client.accountId())) {
        throw new IllegalStateException("Cannot consume message from wrong Simple Client");
      }
      String mainMessage = message;
      if (message.contains("@@")) {
        String[] messageParts = message.split("@@");
        if ("ACKNOWLEDGE".equals(messageParts[1])) {
          LOGGER.debug("Acknowledge was accepted: {}", messageParts[1]);
          UUID msgId;
          try {
            msgId = UUID.fromString(messageParts[0]);
          } catch (Exception ex) {
            String idStr = messageParts[0].substring(0, messageParts[0].lastIndexOf("-"));
            msgId = UUID.fromString(idStr);
          }
          acknowledgeHistory.put(msgId, getCurrentTimestampMs());
          return;
        }
        sendAcknowledge(messageParts[0]);
        mainMessage = messageParts[1];
      }
      if (JsonUtility.isValid(mainMessage)) {
        TestMessage testMessage = JsonUtility.fromJson(mainMessage, TestMessage.class);
        LOGGER.debug("Message with id {} was received", testMessage.id());
        messageHistory.put(
            UUID.fromString(testMessage.id()),
            new TestMessage(
                testMessage.receiver(),
                testMessage.id(),
                testMessage.sendTime(),
                testMessage.accountId(),
                getCurrentTimestampMs(),
                testMessage.preserveOrder(),
                testMessage.iteration()
            )
        );
      }
    };
    dataConsumer = (ws, data) -> {
      byte[] binary = data.array();
      final int clientHash = BmvObjectUtils.bytesToInt(
          Arrays.copyOfRange(binary, 0, 4)
      );
      if (clientHash != new PClient(this.client).hashCode()) {
        throw new IllegalStateException("Cannot consume message from wrong Simple Client");
      }
      boolean withAcknowledge = BmvObjectUtils.bytesToBoolean(
          Arrays.copyOfRange(binary, 4, 5)
      );
      final UUID binaryId = BmvObjectUtils.bytesToUuid(Arrays.copyOfRange(binary, 5, 21));
      final int order = BmvObjectUtils.bytesToInt(Arrays.copyOfRange(binary, 21, 25));

      String payload = new String(
          Base64.getDecoder().decode(Arrays.copyOfRange(binary, 25, binary.length)),
          StandardCharsets.UTF_8
      );
      if (JsonUtility.isValid(payload)) {
        TestMessage testMessage = JsonUtility.fromJson(payload, TestMessage.class);
        if (!testMessage.id().equals(binaryId.toString())) {
          throw new IllegalStateException("Message id check was not passed");
        }
        messageHistory.put(
            UUID.fromString(testMessage.id()),
            new TestMessage(
                testMessage.receiver(),
                testMessage.id(),
                testMessage.sendTime(),
                testMessage.accountId(),
                getCurrentTimestampMs(),
                testMessage.preserveOrder(),
                testMessage.iteration()
            )
        );
      }
      if (withAcknowledge) {
        sendAcknowledge(buildId(binaryId, order));
        //send(Arrays.copyOfRange(binary, 0, 25));
      }
    };
    afterCloseListener = (code, reason) -> {
      synchronized (this) {
        if (code != 1000) {
          brokenConnectionsCounter.incrementAndGet();
        }
        pool = pool.stream().filter(this::isOpen).collect(Collectors.toList());
        rotator = new Rotator(pool::size);
      }
    };
  }

  private boolean isOpen(SimpleClient simpleClient) {
    try {
      return simpleClient.isOpen();
    } catch (Exception ex) {
      return false;
    }
  }

  public PClientWithPusherId getClient() {
    return client;
  }

  public void sendAcknowledge(String messageId) {
    send(PushcaMessageFactoryService.buildCommandMessage(
        Command.ACKNOWLEDGE,
        Map.of("messageId", messageId)
    ).command());
  }

  public synchronized void init(String pusherId) {
    pool.clear();
    pool.addAll(
        openConnectionFactory.openConnectionPool(pushcaBaseUrl,
            client, pusherId, poolSize, messageConsumer,
            dataConsumer, afterCloseListener)
    );
    rotator = new Rotator(pool::size);
    LOGGER.info("Connection pool was initialized: account {}", client.accountId());
  }

  public synchronized void init() {
    init(null);
  }

  public SimpleClient getFirstOpen() {
    /*int index = calculateEvenDistributedIndex(pool.size());
    SimpleClient ws = pool.get(index);
    if (isOpen(ws)) {
      return ws;
    }
    throw new IllegalStateException("Dead connection: account id = " + client.accountId());*/
    for (int i = 0; i < (pool.size() + 1); i++) {
      SimpleClient ws = pool.get(rotator.getNext());
      if (isOpen(ws)) {
        return ws;
      }
    }
    throw new IllegalStateException("Pool is exhausted: account id = " + client.accountId());
  }

  public List<SimpleClient> getPool() {
    return pool;
  }

  public void send(String message) {
    getFirstOpen().send(message);
  }

  public void send(byte[] data) {
    getFirstOpen().send(data);
  }

  public byte[] getBinaryHistory() {
    return binaryHistory.entrySet().stream()
        .sorted(Comparator.comparingInt(Entry::getKey))
        .map(Entry::getValue)
        .reduce(ArrayUtils::addAll)
        .orElse(null);
  }

  public int getBinaryHistoryTotalSize() {
    return binaryHistory.values().stream().map(bytes -> bytes.length)
        .reduce(Integer::sum).orElse(0);
  }

  public Map<UUID, Long> getAcknowledgeHistory() {
    return acknowledgeHistory;
  }

  public TestMessage findValidMessageInHistory(TestMessage message) {
    UUID messageId = UUID.fromString(message.id());
    TestMessage tm = messageHistory.get(messageId);
    if (tm == null) {
      return null;
    }
    if (!message.accountId().equals(client.accountId())) {
      LOGGER.error("Wrong receiver for message: expected {}, received by {}", message.accountId(),
          client.accountId());
      return null;
    }
    return tm;
  }

  public void resetHistory() {
    messageHistory.clear();
    binaryHistory.clear();
  }

  public void closeAll() {
    pool.forEach(SimpleClient::close);
    pool.clear();
  }

  private int calculateRandomPosition(int bound) {
    if (bound == 1) {
      return 0;
    }
    return Math.toIntExact(System.currentTimeMillis() % bound);
  }
}
