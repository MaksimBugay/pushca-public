package bmv.org.pushca.client;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.PClient;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;

public class PushcaWebSocketBuilder {

  private final String pushcaApiUrl;
  private String pusherId;
  private final PClient client;
  private int connectTimeoutMs = 1000;
  private BiConsumer<WebSocketApi, String> messageConsumer;
  private BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer;
  private BiConsumer<WebSocketApi, Binary> dataConsumer;
  private Consumer<String> acknowledgeConsumer;
  private Consumer<BinaryObjectData> binaryManifestConsumer;
  private BiConsumer<Integer, String> onCloseListener;

  private SSLContext sslContext;

  public PushcaWebSocketBuilder(String pushcaApiUrl, PClient client) {
    this.pushcaApiUrl = pushcaApiUrl;
    this.client = client;
  }

  public PushcaWebSocketBuilder withPusherId(String pusherId) {
    this.pusherId = pusherId;
    return this;
  }

  public PushcaWebSocketBuilder withConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
    return this;
  }

  public PushcaWebSocketBuilder withMessageConsumer(
      BiConsumer<WebSocketApi, String> messageConsumer) {
    this.messageConsumer = messageConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withBinaryMessageConsumer(
      BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer) {
    this.binaryMessageConsumer = binaryMessageConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withDataConsumer(
      BiConsumer<WebSocketApi, Binary> dataConsumer) {
    this.dataConsumer = dataConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withAcknowledgeConsumer(Consumer<String> acknowledgeConsumer) {
    this.acknowledgeConsumer = acknowledgeConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withBinaryManifestConsumer(
      Consumer<BinaryObjectData> binaryManifestConsumer) {
    this.binaryManifestConsumer = binaryManifestConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withOnCloseListener(
      BiConsumer<Integer, String> onCloseListener) {
    this.onCloseListener = onCloseListener;
    return this;
  }

  public PushcaWebSocketBuilder withSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
    return this;
  }

  public PushcaWebSocket build() {
    return new PushcaWebSocket(pushcaApiUrl, pusherId, client, connectTimeoutMs, messageConsumer,
        binaryMessageConsumer, dataConsumer, acknowledgeConsumer, binaryManifestConsumer,
        onCloseListener, sslContext);
  }
}
