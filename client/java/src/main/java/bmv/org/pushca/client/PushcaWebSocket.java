package bmv.org.pushca.client;

import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.serialization.json.JsonUtility;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.function.BiConsumer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

public class PushcaWebSocket extends WebSocketClient implements WebSocketApi {

  private final String pusherId;

  private final String baseWsUrl;

  private final String token;

  private final PClient client;

  public static URI acquireWsConnectionUrl(String pushcaApiUrl, String pusherId, PClient client) {
    try {
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
      OpenConnectionResponse response =
          JsonUtility.fromJson(responseJson.toString(), OpenConnectionResponse.class);
      return new URI(response.externalAdvertisedUrl);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<PushcaWebSocket, String> messageConsumer,
      BiConsumer<PushcaWebSocket, ByteBuffer> dataConsumer) {
    super(acquireWsConnectionUrl(pushcaApiUrl, pusherId, client),
        new Draft_6455(), new HashMap<>(), connectTimeoutMs);
    this.pusherId = pusherId;
    String wsUrl = this.getURI().toString();
    this.baseWsUrl = wsUrl.substring(0, wsUrl.lastIndexOf('/') + 1);
    this.token = wsUrl.substring(wsUrl.lastIndexOf('/') + 1);
    this.client = client;
  }

  @Override
  public void onOpen(ServerHandshake handshakeData) {
    System.out.println(toJson(client));
    System.out.println(baseWsUrl);
    System.out.println(token);
  }

  @Override
  public void onMessage(String message) {

  }

  @Override
  public void onClose(int code, String reason, boolean remote) {

  }

  @Override
  public void onError(Exception ex) {

  }

}
