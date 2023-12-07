package bmv.org.pushca.client;

import static bmv.org.pushca.client.model.Command.ACKNOWLEDGE;
import static bmv.org.pushca.client.model.Command.REFRESH_TOKEN;
import static bmv.org.pushca.client.model.Command.SEND_MESSAGE;
import static bmv.org.pushca.client.model.Command.SEND_MESSAGE_WITH_ACKNOWLEDGE;
import static bmv.org.pushca.client.model.WebSocketState.CLOSING;
import static bmv.org.pushca.client.model.WebSocketState.NOT_YET_CONNECTED;
import static bmv.org.pushca.client.model.WebSocketState.PERMANENTLY_CLOSED;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.CommandWithMetaData;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.WebSocketState;
import bmv.org.pushca.client.serialization.json.JsonUtility;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket implements Closeable {

  public static final String ACKNOWLEDGE_PREFIX = "ACKNOWLEDGE@@";
  public static final String TOKEN_PREFIX = "TOKEN@@";
  public static final String BINARY_MANIFEST_PREFIX = "BINARY_MANIFEST@@";
  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);
  private static final long REFRESH_TOKEN_INTERVAL_MS = Duration.ofMinutes(10).toMillis();
  private static final int[] RECONNECT_INTERVALS =
      {0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597};
  private final String pusherId;

  private final String baseWsUrl;

  private final AtomicReference<String> tokenHolder = new AtomicReference<>();

  private final PClient client;

  private WebSocketApi webSocket;

  private final AtomicReference<WebSocketState> stateHolder = new AtomicReference<>();

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private final AtomicLong iterationCounter = new AtomicLong();

  private final AtomicLong lastTokenRefreshTime = new AtomicLong();

  private final AtomicInteger errorCounter = new AtomicInteger();

  private final AtomicInteger reConnectIndex = new AtomicInteger();

  PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, ByteBuffer> dataConsumer,
      Consumer<String> acknowledgeConsumer,
      Consumer<String> binaryManifestConsumer,
      BiConsumer<Integer, String> onCloseListener) {
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
            dataConsumer,
            onCloseListener);
        scheduler.scheduleAtFixedRate(this::keepAliveJob, 2L * connectTimeoutMs, connectTimeoutMs,
            TimeUnit.MILLISECONDS);
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

  private void processMessage(WebSocketApi ws, String inMessage,
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
      Optional.ofNullable(binaryManifestConsumer)
          .ifPresent(c -> c.accept(inMessage.replace(BINARY_MANIFEST_PREFIX, "")));
      return;
    }
    if (message.contains("@@")) {
      String[] parts = message.split("@@");
      //send acknowledge
      Map<String, Object> metaData = new HashMap<>();
      metaData.put("messageId", parts[0]);
      webSocket.send(toJson(new CommandWithMetaData(ACKNOWLEDGE, metaData)));
      message = parts[1];
    }
    if (messageConsumer != null) {
      messageConsumer.accept(ws, message);
    }
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

  public void sendMessage(String id, PClient dest, boolean preserveOrder, String message) {
    sendMessage(id, new ClientFilter(dest), preserveOrder, message);
  }

  public void sendMessage(PClient dest, String message) {
    sendMessage(null, dest, false, message);
  }

  private void keepAliveJob() {
    if (stateHolder.get() == PERMANENTLY_CLOSED) {
      return;
    }
    long i = iterationCounter.incrementAndGet();
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
          webSocket.getOnCloseListener()
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
    return JsonUtility.fromJson(responseJson.toString(), OpenConnectionResponse.class);
  }

  @Override
  public void close() {
    scheduler.shutdown();
    webSocket.close();
  }
}
