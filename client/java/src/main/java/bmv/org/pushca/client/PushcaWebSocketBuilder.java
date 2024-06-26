package bmv.org.pushca.client;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UnknownDatagram;
import bmv.org.pushca.client.transformation.BinaryPayloadTransformer;
import bmv.org.pushca.client.transformation.base64.Base64PayloadTransformer;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.gateway.GatewayRequestHeader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.net.ssl.SSLContext;

public class PushcaWebSocketBuilder {

  private final String pushcaApiUrl;
  private String pusherId;
  private final PClient client;
  private final String apiKey;
  private final String passwordHash;
  private int connectTimeoutMs = 0;
  private BiConsumer<PushcaWebSocketApi, String> messageConsumer;
  private BiConsumer<PushcaWebSocketApi, Binary> dataConsumer;
  private BiConsumer<PushcaWebSocketApi, UnknownDatagram> unknownDatagramConsumer;
  private BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer;
  private BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer;
  private Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors =
      new HashMap<>();
  private BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer;
  private BiConsumer<Integer, String> onCloseListener;
  private BinaryPayloadTransformer binaryPayloadTransformer = new Base64PayloadTransformer();
  private SSLContext sslContext;
  private WsConnectionFactory wsConnectionFactory = new WsConnectionWithJavaWebSocketFactory();

  public PushcaWebSocketBuilder(String pushcaApiUrl, String apiKey, PClient client,
      String passwordHash) {
    this.pushcaApiUrl = pushcaApiUrl;
    this.apiKey = apiKey;
    this.client = client;
    this.passwordHash = passwordHash;
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
      BiConsumer<PushcaWebSocketApi, String> messageConsumer) {
    this.messageConsumer = messageConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withDataConsumer(
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer) {
    this.dataConsumer = dataConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withUnknownDatagramConsumer(
      BiConsumer<PushcaWebSocketApi, UnknownDatagram> unknownDatagramConsumer) {
    this.unknownDatagramConsumer = unknownDatagramConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withChannelEventConsumer(
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer) {
    this.channelEventConsumer = channelEventConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withChannelMessageConsumer(
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer) {
    this.channelMessageConsumer = channelMessageConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withGatewayProcessors(
      Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors) {
    this.gatewayProcessors = gatewayProcessors;
    return this;
  }

  public PushcaWebSocketBuilder withBinaryManifestConsumer(
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer) {
    this.binaryManifestConsumer = binaryManifestConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withBinaryPayloadTransformer(
      BinaryPayloadTransformer binaryPayloadTransformer) {
    this.binaryPayloadTransformer = binaryPayloadTransformer;
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

  public PushcaWebSocketBuilder withWsConnectionFactory(WsConnectionFactory wsConnectionFactory) {
    this.wsConnectionFactory = wsConnectionFactory;
    return this;
  }

  public PushcaWebSocket build() {
    return new PushcaWebSocket(pushcaApiUrl, pusherId, apiKey, client, passwordHash,
        connectTimeoutMs, messageConsumer, dataConsumer, unknownDatagramConsumer,
        binaryManifestConsumer, channelEventConsumer, channelMessageConsumer, gatewayProcessors,
        binaryPayloadTransformer, onCloseListener, sslContext, wsConnectionFactory);
  }
}
