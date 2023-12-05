package bmv.org.pushca.client;

import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.serialization.json.JsonUtility;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.java_websocket.enums.ReadyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket {

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);

  private final String pusherId;

  private final String baseWsUrl;

  private final String token;

  private final PClient client;

  private final WebSocketApi webSocket;

  private final AtomicReference<ReadyState> stateHolder = new AtomicReference<>();

  public PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<JavaWebSocket, String> messageConsumer,
      BiConsumer<JavaWebSocket, ByteBuffer> dataConsumer,
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
        this.token = wsUrl.toString().substring(wsUrl.toString().lastIndexOf('/') + 1);
        this.webSocket =
            new JavaWebSocket(wsUrl, connectTimeoutMs, messageConsumer, dataConsumer,
                onCloseListener);
        this.webSocket.connect();
        LOGGER.debug("Connection attributes: baseUrl {}, token {}", baseWsUrl, token);
      } else {
        this.webSocket = null;
        this.baseWsUrl = null;
        this.token = null;
      }
    } else {
      this.pusherId = pusherId;
      this.baseWsUrl = null;
      this.token = null;
      this.webSocket = null;
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
}
