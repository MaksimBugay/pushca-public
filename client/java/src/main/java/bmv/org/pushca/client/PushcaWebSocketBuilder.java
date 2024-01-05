package bmv.org.pushca.client;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UnknownDatagram;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;

public class PushcaWebSocketBuilder {

  private final String pushcaApiUrl;
  private String pusherId;
  private final PClient client;
  private int connectTimeoutMs = 0;
  private BiConsumer<PushcaWebSocketApi, String> messageConsumer;
  private BiConsumer<PushcaWebSocketApi, byte[]> binaryMessageConsumer;
  private BiConsumer<PushcaWebSocketApi, Binary> dataConsumer;
  private BiConsumer<PushcaWebSocketApi, UnknownDatagram> unknownDatagramConsumer;
  private BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer;
  private BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer;
  private BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer;
  private BiConsumer<Integer, String> onCloseListener;
  private SSLContext sslContext;
  private WsConnectionFactory wsConnectionFactory = new WsConnectionWithJavaWebSocketFactory();

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
      BiConsumer<PushcaWebSocketApi, String> messageConsumer) {
    this.messageConsumer = messageConsumer;
    return this;
  }

  public PushcaWebSocketBuilder withBinaryMessageConsumer(
      BiConsumer<PushcaWebSocketApi, byte[]> binaryMessageConsumer) {
    this.binaryMessageConsumer = binaryMessageConsumer;
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

  public PushcaWebSocketBuilder withBinaryManifestConsumer(
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer) {
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

  public PushcaWebSocketBuilder withWsConnectionFactory(WsConnectionFactory wsConnectionFactory) {
    this.wsConnectionFactory = wsConnectionFactory;
    return this;
  }

  public PushcaWebSocket build() {
    return new PushcaWebSocket(pushcaApiUrl, pusherId, client, connectTimeoutMs, messageConsumer,
        binaryMessageConsumer, dataConsumer, unknownDatagramConsumer, binaryManifestConsumer,
        channelEventConsumer, channelMessageConsumer, onCloseListener, sslContext,
        wsConnectionFactory);
  }
}
