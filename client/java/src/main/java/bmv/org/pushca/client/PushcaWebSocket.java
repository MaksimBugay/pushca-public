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
import static bmv.org.pushca.client.utils.SendBinaryHelper.toBinaryObjectMetadata;

import bmv.org.pushca.client.model.BinaryObjectMetadata;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.CommandWithMetaData;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket implements Closeable, PushcaWebSocketApi {

  public static final String ACKNOWLEDGE_PREFIX = "ACKNOWLEDGE@@";
  public static final String TOKEN_PREFIX = "TOKEN@@";
  public static final String BINARY_MANIFEST_PREFIX = "BINARY_MANIFEST@@";
  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);
  private static final long REFRESH_TOKEN_INTERVAL_MS = Duration.ofMinutes(10).toMillis();
  private static final int[] RECONNECT_INTERVALS =
      {0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597};
  public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
  private final String pusherId;

  private final String baseWsUrl;

  private final AtomicReference<String> tokenHolder = new AtomicReference<>();

  private final PClient client;

  private WebSocketApi webSocket;

  private final AtomicReference<WebSocketState> stateHolder = new AtomicReference<>();

  private ScheduledExecutorService scheduler;

  private final AtomicLong lastTokenRefreshTime = new AtomicLong();

  private final AtomicInteger errorCounter = new AtomicInteger();

  private final AtomicInteger reConnectIndex = new AtomicInteger();

  private final Map<String, BinaryObjectMetadata> manifests = new ConcurrentHashMap<>();

  PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer,
      Consumer<String> acknowledgeConsumer,
      Consumer<String> binaryManifestConsumer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext) {
    this.client = client;
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
        this.webSocket = new JavaWebSocket(wsUrl, connectTimeoutMs,
            (ws, message) -> processMessage(ws, message, messageConsumer, acknowledgeConsumer,
                binaryManifestConsumer),
            (ws, byteBuffer) -> processBinary(ws, byteBuffer, dataConsumer),
            onCloseListener, sslContext);
        scheduler = createScheduler(
            this::keepAliveJob,
            Duration.ofMillis(connectTimeoutMs),
            Duration.ofMillis(2L * connectTimeoutMs)
        );
        this.webSocket.connect();
        LOGGER.debug("Connection attributes: baseUrl {}, token {}", baseWsUrl, tokenHolder);
      } else {
        this.baseWsUrl = null;
      }
    } else {
      this.pusherId = pusherId;
      this.baseWsUrl = null;
    }
  }

  public void processBinary(WebSocketApi ws, ByteBuffer byteBuffer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer) {
    try {
      byte[] binary = byteBuffer.array();
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

      BinaryObjectMetadata manifest = manifests.get(binaryId.toString());
      if (manifest == null) {
        throw new IllegalStateException("Unknown binary with id = " + binaryId);
      }
      Datagram datagram = manifest.getDatagram(binaryId.toString(), order);
      if (datagram == null) {
        throw new IllegalArgumentException(
            MessageFormat.format("Unknown datagram: binaryId={0}, order={1}", binaryId.toString(),
                String.valueOf(order))
        );
      }
      byte[] payload = Arrays.copyOfRange(binary, 25, binary.length);
      if (!datagram.md5.equals(calculateSha256(payload))) {
        throw new IllegalArgumentException(
            MessageFormat.format("Md5 validation was not passed: binaryId={0}, order={1}",
                binaryId.toString(),
                String.valueOf(order))
        );
      }
      Optional.ofNullable(dataConsumer).ifPresent(c -> c.accept(webSocket, payload));
      if (withAcknowledge) {
        webSocket.send(Arrays.copyOfRange(binary, 0, 25));
      }
      manifests.computeIfPresent(manifest.getBinaryId(), (k, v) -> {
        v.markDatagramAsReceived(datagram.id, datagram.order);
        return v;
      });
      boolean allDatagramsWereReceived = manifest.getDatagrams().stream().allMatch(d -> d.received);
      if (allDatagramsWereReceived) {
        manifests.remove(manifest.getBinaryId());
        LOGGER.info("Binary was successfully received: id {}, name {}", manifest.getBinaryId(),
            manifest.name);
      }
    } finally {
      byteBuffer.clear();
    }
  }

  public void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<String> binaryManifestConsumer) {
    String message = inMessage;
    if (StringUtils.isEmpty(message)) {
      return;
    }
    if (message.startsWith(ACKNOWLEDGE_PREFIX)) {
      Optional.ofNullable(acknowledgeConsumer)
          .ifPresent(ac -> ac.accept(inMessage.replace(ACKNOWLEDGE_PREFIX, "")));
      return;
    }
    if (message.startsWith(TOKEN_PREFIX)) {
      tokenHolder.set(message.replace(TOKEN_PREFIX, ""));
      LOGGER.debug("New token was acquired: {}", tokenHolder.get());
      return;
    }
    if (message.startsWith(BINARY_MANIFEST_PREFIX)) {
      String manifestJsom = inMessage.replace(BINARY_MANIFEST_PREFIX, "");
      BinaryObjectMetadata manifest = fromJson(manifestJsom, BinaryObjectMetadata.class);
      manifests.put(manifest.getBinaryId(), manifest);
      if (binaryManifestConsumer != null) {
        binaryManifestConsumer.accept(manifestJsom);
      }
      return;
    }
    if (message.contains("@@")) {
      String[] parts = message.split("@@");
      sendAcknowledge(parts[0]);
      message = parts[1];
    }
    if (messageConsumer != null) {
      messageConsumer.accept(ws, message);
    }
  }

  public void sendAcknowledge(String id) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("messageId", id);
    webSocket.send(toJson(new CommandWithMetaData(ACKNOWLEDGE, metaData)));
  }

  public void sendMessageWithAcknowledge(String id, PClient dest, boolean preserveOrder,
      String message) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("client", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);
    final CommandWithMetaData command =
        new CommandWithMetaData(SEND_MESSAGE_WITH_ACKNOWLEDGE, metaData);
    webSocket.send(toJson(command));
  }

  public void sendMessageWithAcknowledge(String id, PClient dest, String message) {
    sendMessageWithAcknowledge(id, dest, false, message);
  }

  public void sendMessage(String id, ClientFilter dest, boolean preserveOrder, String message) {
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

  public void sendMessage(ClientFilter dest, String message) {
    sendMessage(null, dest, false, message);
  }

  public void sendMessage(String id, PClient dest, boolean preserveOrder, String message) {
    sendMessage(id, new ClientFilter(dest), preserveOrder, message);
  }

  public void sendMessage(PClient dest, String message) {
    sendMessage(null, dest, false, message);
  }

  public void sendBinary(PClient dest, byte[] data) {
    sendBinary(dest, data, false);
  }

  public void sendBinary(PClient dest, byte[] data, boolean withAcknowledge) {
    sendBinary(dest, data, null, null, DEFAULT_CHUNK_SIZE, withAcknowledge, false);
  }

  public void sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge, boolean manifestOnly) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    BinaryObjectMetadata binaryMetadata = toBinaryObjectMetadata(
        dest,
        id,
        name,
        client,
        BmvObjectUtils.splitToChunks(data, chunkSize),
        pusherId,
        withAcknowledge
    );
    sendMessage(dest, buildBinaryManifest(binaryMetadata));
    if (manifestOnly) {
      return;
    }
    binaryMetadata.getDatagrams().forEach(datagram -> webSocket.send(datagram.preparedDataWithPrefix));
  }

  private void keepAliveJob() {
    if (stateHolder.get() == PERMANENTLY_CLOSED) {
      return;
    }
    stateHolder.set(webSocket.getWebSocketState());
    if (webSocket.isOpen()) {
      errorCounter.set(0);
      reConnectIndex.set(0);
      if (lastTokenRefreshTime.get() == 0
          || System.currentTimeMillis() - lastTokenRefreshTime.get() > REFRESH_TOKEN_INTERVAL_MS) {
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
    if (reConnectIndex.get() > RECONNECT_INTERVALS.length - 1) {
      stateHolder.set(PERMANENTLY_CLOSED);
      LOGGER.error("Web socket was permanently closed: client {}", toJson(client));
      return;
    }
    if (errorCounter.getAndIncrement() == RECONNECT_INTERVALS[reConnectIndex.get()]) {
      reConnectIndex.incrementAndGet();
      reConnect();
    }
  }

  private void reConnect() {
    try {
      webSocket = new JavaWebSocket(
          new URI(baseWsUrl + tokenHolder.get()),
          webSocket.getConnectTimeoutMs(),
          webSocket.getMessageConsumer(),
          webSocket.getDataConsumer(),
          webSocket.getOnCloseListener(),
          webSocket.getSslContext()
      );
      webSocket.connect();
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

  public String buildBinaryManifest(BinaryObjectMetadata binaryObjectMetadata) {
    return MessageFormat.format("{0}{1}", BINARY_MANIFEST_PREFIX, toJson(binaryObjectMetadata));
  }

  @Override
  public void close() {
    Optional.ofNullable(scheduler).ifPresent(ExecutorService::shutdown);
    webSocket.close();
  }
}
