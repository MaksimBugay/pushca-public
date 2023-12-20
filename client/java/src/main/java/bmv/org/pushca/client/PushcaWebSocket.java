package bmv.org.pushca.client;

import static bmv.org.pushca.client.model.Command.ACKNOWLEDGE;
import static bmv.org.pushca.client.model.Command.REFRESH_TOKEN;
import static bmv.org.pushca.client.model.Command.SEND_MESSAGE;
import static bmv.org.pushca.client.model.Command.SEND_MESSAGE_WITH_ACKNOWLEDGE;
import static bmv.org.pushca.client.model.WebSocketState.CLOSING;
import static bmv.org.pushca.client.model.WebSocketState.NOT_YET_CONNECTED;
import static bmv.org.pushca.client.model.WebSocketState.PERMANENTLY_CLOSED;
import static bmv.org.pushca.client.serialization.json.JsonUtility.fromJson;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.calculateSha256;
import static bmv.org.pushca.client.utils.BmvObjectUtils.createScheduler;
import static bmv.org.pushca.client.utils.BmvObjectUtils.toBinary;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toBinaryObjectData;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toDatagramPrefix;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.CommandWithMetaData;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UnknownDatagram;
import bmv.org.pushca.client.model.WebSocketState;
import bmv.org.pushca.client.utils.BmvObjectUtils;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket implements Closeable, PushcaWebSocketApi {

  public static final String PUSHCA_INTERNAL_MESSAGE_DELIMITER = "@@";
  public static final String ACKNOWLEDGE_PREFIX = "ACKNOWLEDGE" + PUSHCA_INTERNAL_MESSAGE_DELIMITER;
  public static final String TOKEN_PREFIX = "TOKEN" + PUSHCA_INTERNAL_MESSAGE_DELIMITER;
  public static final String BINARY_MANIFEST_BARE_PREFIX = "BINARY_MANIFEST";
  public static final String BINARY_MANIFEST_PREFIX =
      BINARY_MANIFEST_BARE_PREFIX + PUSHCA_INTERNAL_MESSAGE_DELIMITER;
  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);
  private static final long REFRESH_TOKEN_INTERVAL_MS = Duration.ofMinutes(10).toMillis();
  private static final List<Integer> RECONNECT_INTERVALS = Arrays.asList(
      0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597
  );
  public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
  public static final int MAX_REPEAT_ATTEMPT_NUMBER = 3;
  public static final int ACKNOWLEDGE_TIMEOUT_SEC = 10;
  private final String pusherId;

  private final String baseWsUrl;

  private final AtomicReference<String> tokenHolder = new AtomicReference<>();

  private final PClient client;

  private WebSocketApi webSocket;

  private final AtomicReference<WebSocketState> stateHolder = new AtomicReference<>();

  private ScheduledExecutorService scheduler;

  private final AtomicLong lastTokenRefreshTime = new AtomicLong();

  private final AtomicInteger reConnectIndex = new AtomicInteger();

  private final Map<String, BinaryObjectData> binaries = new ConcurrentHashMap<>();

  private final Map<String, CompletableFuture<String>> waitingHall = new ConcurrentHashMap<>();
  private final BiConsumer<WebSocketApi, String> wsMessageConsumer;
  private final BiConsumer<WebSocketApi, byte[]> wsDataConsumer;
  private final BiConsumer<Integer, String> wsOnCloseListener;
  private final SSLContext wsSslContext;
  private final int wsConnectTimeoutMs;

  private final WsConnectionFactory wsConnectionFactory;

  private final ScheduledExecutorService acknowledgeTimeoutScheduler =
      Executors.newScheduledThreadPool(10);

  public static String buildAcknowledgeId(String binaryId, int order) {
    return MessageFormat.format("{0}-{1}", binaryId, String.valueOf(order));
  }

  PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer,
      BiConsumer<WebSocketApi, Binary> dataConsumer,
      BiConsumer<WebSocketApi, UnknownDatagram> unknownDatagramConsumer,
      Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectData> binaryManifestConsumer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext,
      WsConnectionFactory wsConnectionFactory) {
    this.wsConnectionFactory = wsConnectionFactory;
    this.client = client;
    this.wsMessageConsumer =
        (ws, message) -> processMessage(ws, message, messageConsumer, acknowledgeConsumer,
            binaryManifestConsumer);
    this.wsDataConsumer =
        (ws, byteBuffer) -> processBinary(ws, byteBuffer, dataConsumer, unknownDatagramConsumer,
            binaryMessageConsumer);
    this.wsOnCloseListener = onCloseListener;
    this.wsSslContext = sslContext;
    this.wsConnectTimeoutMs = connectTimeoutMs;
    OpenConnectionResponse openConnectionResponse = null;
    try {
      openConnectionResponse = openConnection(pushcaApiUrl, pusherId, client);
    } catch (IOException e) {
      LOGGER.error("Cannot open websocket connection: client {}, pusher id {}", toJson(client),
          pusherId);
      this.stateHolder.set(WebSocketState.CLOSED);
    }

    if (openConnectionResponse != null) {
      this.pusherId = openConnectionResponse.pusherInstanceId;

      URI wsUrl = null;
      try {
        wsUrl = new URI(openConnectionResponse.externalAdvertisedUrl);
      } catch (URISyntaxException e) {
        LOGGER.error("Malformed web socket url: {}", openConnectionResponse.externalAdvertisedUrl);
        this.stateHolder.set(WebSocketState.CLOSED);
      }

      if (wsUrl != null) {
        this.baseWsUrl = wsUrl.toString().substring(0, wsUrl.toString().lastIndexOf('/') + 1);
        this.tokenHolder.set(wsUrl.toString().substring(wsUrl.toString().lastIndexOf('/') + 1));
        this.webSocket = this.wsConnectionFactory.createConnection(wsUrl,
            this.wsConnectTimeoutMs,
            this.wsMessageConsumer,
            this.wsDataConsumer,
            this.wsOnCloseListener,
            this.wsSslContext);
        scheduler = createScheduler(
            this::keepAliveJob,
            Duration.ofSeconds(1),
            Duration.ofSeconds(2)
        );
        LOGGER.debug("Connection attributes: baseUrl {}, token {}", baseWsUrl, tokenHolder);
      } else {
        this.baseWsUrl = null;
      }
    } else {
      this.pusherId = pusherId;
      this.baseWsUrl = null;
    }
  }

  public void processBinary(WebSocketApi ws, byte[] binary,
      BiConsumer<WebSocketApi, Binary> dataConsumer,
      BiConsumer<WebSocketApi, UnknownDatagram> unknownDatagramConsumer,
      BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer) {
    final int clientHash = BmvObjectUtils.bytesToInt(
        Arrays.copyOfRange(binary, 0, 4)
    );
    if (clientHash != client.hashCode()) {
      throw new IllegalStateException("Data was intended for another client");
    }
    boolean withAcknowledge = BmvObjectUtils.bytesToBoolean(
        Arrays.copyOfRange(binary, 4, 5)
    );
    final UUID binaryId = BmvObjectUtils.bytesToUuid(Arrays.copyOfRange(binary, 5, 21));
    final int order = BmvObjectUtils.bytesToInt(Arrays.copyOfRange(binary, 21, 25));

    //binary message was received
    if (Integer.MAX_VALUE == order) {
      Optional.ofNullable(binaryMessageConsumer)
          .ifPresent(c -> c.accept(webSocket, Arrays.copyOfRange(binary, 25, binary.length)));
      if (withAcknowledge) {
        sendAcknowledge(binaryId, order);
      }
      return;
    }

    BinaryObjectData binaryData = binaries.computeIfPresent(binaryId.toString(), (k, v) -> {
      v.fillWithReceivedData(order, Arrays.copyOfRange(binary, 25, binary.length));
      return v;
    });
    if (binaryData == null) {
      if (unknownDatagramConsumer != null) {
        unknownDatagramConsumer.accept(webSocket, new UnknownDatagram(
            binaryId,
            Arrays.copyOfRange(binary, 0, 25),
            order,
            Arrays.copyOfRange(binary, 25, binary.length)
        ));
        return;
      }
      throw new IllegalStateException("Unknown binary with id = " + binaryId);
    }
    Datagram datagram = binaryData.getDatagram(order);
    if (datagram == null) {
      throw new IllegalArgumentException(
          MessageFormat.format("Unknown datagram: binaryId={0}, order={1}", binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (!datagram.md5.equals(calculateSha256(datagram.data))) {
      throw new IllegalArgumentException(
          MessageFormat.format("Md5 validation was not passed: binaryId={0}, order={1}",
              binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (datagram.size != datagram.data.length) {
      throw new IllegalArgumentException(
          MessageFormat.format("Size validation was not passed: binaryId={0}, order={1}",
              binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (withAcknowledge) {
      sendAcknowledge(binaryId, order);
    }
    if (binaryData.isCompleted()) {
      Optional.ofNullable(dataConsumer).ifPresent(c -> c.accept(webSocket, toBinary(binaryData)));
      binaries.remove(binaryData.getBinaryId());
      LOGGER.info("Binary was successfully received: id {}, name {}", binaryData.getBinaryId(),
          binaryData.name);
    }
  }

  public void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectData> binaryManifestConsumer) {
    String message = inMessage;
    if (StringUtils.isEmpty(message)) {
      return;
    }
    if (message.startsWith(ACKNOWLEDGE_PREFIX)) {
      String id = inMessage.replace(ACKNOWLEDGE_PREFIX, "");
      waitingHall.computeIfPresent(id, (key, callback) -> {
        callback.complete(key);
        return callback;
      });
      Optional.ofNullable(acknowledgeConsumer)
          .ifPresent(ac -> ac.accept(id));
      return;
    }
    if (message.startsWith(TOKEN_PREFIX)) {
      tokenHolder.set(message.replace(TOKEN_PREFIX, ""));
      LOGGER.debug("New token was acquired: {}", tokenHolder.get());
      return;
    }
    if (message.startsWith(BINARY_MANIFEST_PREFIX)) {
      String json = inMessage.replace(BINARY_MANIFEST_PREFIX, "");
      processBinaryManifest(json, binaryManifestConsumer);
      return;
    }
    if (message.contains(PUSHCA_INTERNAL_MESSAGE_DELIMITER)) {
      String[] parts = message.split(PUSHCA_INTERNAL_MESSAGE_DELIMITER);
      sendAcknowledge(parts[0]);
      if (parts.length == 3 && BINARY_MANIFEST_BARE_PREFIX.equals(parts[1])) {
        processBinaryManifest(parts[2], binaryManifestConsumer);
        return;
      }
      message = parts[1];
    }
    if (messageConsumer != null) {
      messageConsumer.accept(ws, message);
    }
  }

  private void processBinaryManifest(String json,
      Consumer<BinaryObjectData> binaryManifestConsumer) {
    BinaryObjectData binaryObjectData = fromJson(json, BinaryObjectData.class);
    if (!binaryObjectData.redOnly) {
      binaries.putIfAbsent(binaryObjectData.getBinaryId(), binaryObjectData);
    }
    if (binaryManifestConsumer != null) {
      binaryManifestConsumer.accept(binaryObjectData);
    }
  }

  public void sendAcknowledge(String id) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("messageId", id);
    webSocket.send(toJson(new CommandWithMetaData(ACKNOWLEDGE, metaData)));
  }

  public void sendAcknowledge(UUID binaryId, int order) {
    String id = buildAcknowledgeId(binaryId.toString(), order);
    sendAcknowledge(id);
  }

  public void sendMessageWithAcknowledge(String msgId, PClient dest, boolean preserveOrder,
      String message) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    String id = StringUtils.isEmpty(msgId) ? UUID.randomUUID().toString() : msgId;
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("client", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);
    final CommandWithMetaData command =
        new CommandWithMetaData(SEND_MESSAGE_WITH_ACKNOWLEDGE, metaData);
    executeWithRepeatOnFailure(
        id,
        () -> webSocket.send(toJson(command))
    );
  }

  public void sendMessageWithAcknowledge(String id, PClient dest, String message) {
    sendMessageWithAcknowledge(id, dest, false, message);
  }

  public void BroadcastMessage(String id, ClientFilter dest, boolean preserveOrder,
      String message) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("filter", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);
    final CommandWithMetaData command = new CommandWithMetaData(SEND_MESSAGE, metaData);
    webSocket.send(toJson(command));
  }

  public void BroadcastMessage(ClientFilter dest, String message) {
    BroadcastMessage(null, dest, false, message);
  }

  public void sendMessage(String id, PClient dest, boolean preserveOrder, String message) {
    BroadcastMessage(id, new ClientFilter(dest), preserveOrder, message);
  }

  public void sendMessage(PClient dest, String message) {
    sendMessage(null, dest, false, message);
  }

  public void sendBinaryMessage(PClient dest, byte[] message, UUID id, boolean withAcknowledge) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    UUID binaryMsgId = id == null ? UUID.randomUUID() : id;
    int order = Integer.MAX_VALUE;
    byte[] prefix = toDatagramPrefix(binaryMsgId, order, dest, withAcknowledge);
    if (withAcknowledge) {
      executeWithRepeatOnFailure(
          buildAcknowledgeId(binaryMsgId.toString(), order),
          () -> webSocket.send(addAll(prefix, message))
      );
    } else {
      webSocket.send(addAll(prefix, message));
    }
  }

  public void sendBinaryMessage(PClient dest, byte[] message) {
    sendBinaryMessage(dest, message, null, false);
  }

  public void sendBinary(PClient dest, byte[] data) {
    sendBinary(dest, data, false);
  }

  public void sendBinary(PClient dest, byte[] data, boolean withAcknowledge) {
    sendBinary(dest, data, null, null, DEFAULT_CHUNK_SIZE, withAcknowledge, false);
  }

  public BinaryObjectData sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge, boolean manifestOnly) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    BinaryObjectData binaryObjectData = toBinaryObjectData(
        dest,
        id,
        name,
        client,
        BmvObjectUtils.splitToChunks(data, chunkSize),
        pusherId,
        withAcknowledge
    );
    binaryObjectData.redOnly = manifestOnly;
    if (withAcknowledge) {
      sendMessageWithAcknowledge(null, dest, buildBinaryManifest(binaryObjectData));
    } else {
      sendMessage(dest, buildBinaryManifest(binaryObjectData));
    }
    if (manifestOnly) {
      return binaryObjectData;
    }
    sendBinary(binaryObjectData, withAcknowledge, null);
    return binaryObjectData;
  }

  public void sendBinary(BinaryObjectData binaryObjectData, boolean withAcknowledge,
      List<String> requestedIds) {
    Predicate<Datagram> filter =
        requestedIds == null ? dgm -> Boolean.TRUE : dgm -> requestedIds.contains(
            buildAcknowledgeId(binaryObjectData.id, dgm.order)
        );
    List<Datagram> datagrams = binaryObjectData.getDatagrams().stream()
        .filter(filter)
        .collect(Collectors.toList());
    if (withAcknowledge) {
      for (Datagram datagram : datagrams) {
        executeWithRepeatOnFailure(
            buildAcknowledgeId(binaryObjectData.id, datagram.order),
            () -> webSocket.send(datagram.data)
        );
      }
    } else {
      datagrams.forEach(datagram -> webSocket.send(datagram.data));
    }
  }

  private CompletableFuture<String> registerAcknowledgeCallback(String id) {
    CompletableFuture<String> ackCallback = new CompletableFuture<>();
    ackCallback.whenComplete((dId, error) -> waitingHall.remove(dId));
    waitingHall.put(id, ackCallback);
    return CompletableFuture.supplyAsync(() -> {
          try {
            return ackCallback.get(ACKNOWLEDGE_TIMEOUT_SEC, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (ExecutionException | TimeoutException error) {
            LOGGER.error("Failed acknowledge: client {}, id {}, error {}", client.accountId, id,
                error.getMessage() == null ? error.getClass().getName() : error.getMessage());
          }
          ackCallback.complete(id);
          return null;
        },
        acknowledgeTimeoutScheduler);
  }

  private void executeWithRepeatOnFailure(String id, Runnable operation) {
    for (int i = 0; i < PushcaWebSocket.MAX_REPEAT_ATTEMPT_NUMBER; i++) {
      operation.run();
      try {
        if (registerAcknowledgeCallback(id).get() != null) {
          return;
        }
      } catch (Exception e) {
        LOGGER.error("Failed execute operation attempt", e);
      }
    }
    throw new RuntimeException("Impossible to complete operation");
  }

  private void keepAliveJob() {
    if (stateHolder.get() == PERMANENTLY_CLOSED) {
      return;
    }
    stateHolder.set(webSocket.getWebSocketState());
    if (webSocket.isOpen()) {
      reConnectIndex.set(0);
      if (lastTokenRefreshTime.get() == 0
          || System.currentTimeMillis() - lastTokenRefreshTime.get() > REFRESH_TOKEN_INTERVAL_MS) {
        removeExpiredManifests();
        lastTokenRefreshTime.set(System.currentTimeMillis());
        webSocket.send(toJson(new CommandWithMetaData(REFRESH_TOKEN)));
      }
      return;
    }
    if (webSocket.getWebSocketState() == CLOSING
        || webSocket.getWebSocketState() == NOT_YET_CONNECTED) {
      return;
    }
    //re-connect attempt
    if (reConnectIndex.get() > 2000) {
      stateHolder.set(PERMANENTLY_CLOSED);
      LOGGER.error("Web socket was permanently closed: client {}", toJson(client));
      return;
    }
    if (RECONNECT_INTERVALS.contains(reConnectIndex.getAndIncrement())) {
      reConnect();
    }
  }

  private void reConnect() {
    try {
      webSocket = wsConnectionFactory.createConnection(
          new URI(baseWsUrl + tokenHolder.get()),
          wsConnectTimeoutMs,
          wsMessageConsumer,
          wsDataConsumer,
          wsOnCloseListener,
          wsSslContext
      );
    } catch (URISyntaxException e) {
      LOGGER.error("Malformed web socket url: {}", baseWsUrl + tokenHolder.get());
    }
  }

  private OpenConnectionResponse openConnection(String pushcaApiUrl, String pusherId,
      PClient client) throws IOException {
    URL url = new URL(pushcaApiUrl + "/open-connection");
    URLConnection httpsConn = url.openConnection();
    httpsConn.addRequestProperty("User-Agent", "Mozilla");
    httpsConn.setRequestProperty("Method", "POST");
    httpsConn.setRequestProperty("Content-Type", "application/json");
    httpsConn.setRequestProperty("Accept", "application/json");
    httpsConn.setDoOutput(true);
    OpenConnectionRequest request = new OpenConnectionRequest(client, pusherId);
    try (OutputStream os = httpsConn.getOutputStream()) {
      byte[] input = toJson(request).getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }
    StringBuilder responseJson = new StringBuilder();
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(httpsConn.getInputStream(), StandardCharsets.UTF_8))) {
      String responseLine;
      while ((responseLine = br.readLine()) != null) {
        responseJson.append(responseLine.trim());
      }
    }
    return fromJson(responseJson.toString(), OpenConnectionResponse.class);
  }

  public String buildBinaryManifest(BinaryObjectData binaryObjectData) {
    return MessageFormat.format("{0}{1}", BINARY_MANIFEST_PREFIX, toJson(binaryObjectData));
  }

  private void removeExpiredManifests() {
    long now = System.currentTimeMillis();
    List<String> ids = binaries.values().stream()
        .filter(b -> (now - b.created) > Duration.ofMinutes(30).toMillis())
        .map(b -> b.id)
        .collect(Collectors.toList());
    ids.forEach(binaries::remove);
  }

  @Override
  public void close() {
    Optional.ofNullable(scheduler).ifPresent(ExecutorService::shutdown);
    webSocket.close();
  }
}
