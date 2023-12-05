package bmv.org.pushca.client;

import static bmv.org.pushca.client.model.Command.ACKNOWLEDGE;
import static bmv.org.pushca.client.model.Command.REFRESH_TOKEN;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.model.CommandWithMetaData;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.ReadyState;
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
  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);

  //private static final long REFRESH_TOKEN_INTERVAL_SEC = Duration.ofMinutes(10).getSeconds;
  private static final long REFRESH_TOKEN_INTERVAL_SEC = Duration.ofSeconds(5).getSeconds();
  private final String pusherId;

  private final String baseWsUrl;

  private final AtomicReference<String> tokenHolder = new AtomicReference<>();

  private final PClient client;

  private final WebSocketApi webSocket;

  private final AtomicReference<ReadyState> stateHolder = new AtomicReference<>();

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private final AtomicLong iterationCounter = new AtomicLong();

  public PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, ByteBuffer> dataConsumer,
      Consumer<String> acknowledgeConsumer,
      BiConsumer<Integer, String> onCloseListener) {
    this.client = client;
    OpenConnectionResponse openConnectionResponse = null;
    try {
      openConnectionResponse = openConnection(pushcaApiUrl, pusherId, client);
    } catch (IOException e) {
      LOGGER.error("Cannot open websocket connection: client {}, pusher id {}", toJson(client),
          pusherId);
      this.stateHolder.set(ReadyState.CLOSED);
    }

    if (openConnectionResponse != null) {
      this.pusherId = openConnectionResponse.pusherInstanceId;

      URI wsUrl = null;
      try {
        wsUrl = new URI(openConnectionResponse.externalAdvertisedUrl);
      } catch (URISyntaxException e) {
        LOGGER.error("Malformed web socket url: {}", openConnectionResponse.externalAdvertisedUrl);
        this.stateHolder.set(ReadyState.CLOSED);
      }

      if (wsUrl != null) {
        this.baseWsUrl = wsUrl.toString().substring(0, wsUrl.toString().lastIndexOf('/') + 1);
        this.tokenHolder.set(wsUrl.toString().substring(wsUrl.toString().lastIndexOf('/') + 1));
        this.webSocket = new JavaWebSocket(wsUrl, connectTimeoutMs,
            (ws, message) -> processMessage(ws, message, messageConsumer, acknowledgeConsumer),
            dataConsumer,
            onCloseListener);
        scheduler.scheduleAtFixedRate(this::keepAliveJob, 2L * connectTimeoutMs, 1000,
            TimeUnit.MILLISECONDS);
        this.webSocket.connect();
        LOGGER.debug("Connection attributes: baseUrl {}, token {}", baseWsUrl, tokenHolder);
      } else {
        this.webSocket = null;
        this.baseWsUrl = null;
      }
    } else {
      this.pusherId = pusherId;
      this.baseWsUrl = null;
      this.webSocket = null;
    }
  }

  private void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer) {
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

  private void keepAliveJob() {
    long i = iterationCounter.incrementAndGet();
    stateHolder.set(webSocket.getWebSocketState());
    if (webSocket.isOpen()) {
      if (i % REFRESH_TOKEN_INTERVAL_SEC == 0) {
        webSocket.send(toJson(new CommandWithMetaData(REFRESH_TOKEN)));
      }
      return;
    }
    //re-connect attempt
    LOGGER.info("Iteration {}", i);
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
  }
}
