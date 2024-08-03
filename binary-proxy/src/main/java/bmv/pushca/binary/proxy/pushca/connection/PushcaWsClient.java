package bmv.pushca.binary.proxy.pushca.connection;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWsClient extends WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger("PushcaWsClient");

  private final String clientId;

  private final Consumer<String> messageConsumer;

  private final BiConsumer<PushcaWsClient, ByteBuffer> dataConsumer;

  private final Consumer<PushcaWsClient> afterOpenListener;
  private final BiConsumer<PushcaWsClient, Integer> afterCloseListener;

  public PushcaWsClient(URI wsUrl, String clientId, int connectTimeoutMs,
      Consumer<String> messageConsumer,
      BiConsumer<PushcaWsClient, ByteBuffer> dataConsumer,
      Consumer<PushcaWsClient> afterOpenListener,
      BiConsumer<PushcaWsClient, Integer> afterCloseListener,
      SSLContext sslContext, String clientIp) {
    super(wsUrl, new Draft_6455(),
        Map.of(
            "client-id", clientId,
            "X-Real-IP", StringUtils.isNotEmpty(clientIp) ? clientIp : "127.0.0.1"
        ),
        connectTimeoutMs);
    if (sslContext != null) {
      this.setSocketFactory(sslContext.getSocketFactory());
    }
    this.clientId = clientId;
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.afterOpenListener = afterOpenListener;
    this.afterCloseListener = afterCloseListener;
  }

  public String getClientId() {
    return clientId;
  }

  @Override
  public void onOpen(ServerHandshake handShakeData) {
    LOGGER.info("New connection opened, client id {}", clientId);
    Optional.ofNullable(afterOpenListener).ifPresent(listener -> listener.accept(this));
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    LOGGER.info("Connection was closed: client id {}, exit code {}, additional info {}",
        clientId, code, reason);
    Optional.ofNullable(afterCloseListener)
        .ifPresent(afterCloseListener -> afterCloseListener.accept(this, code));
  }

  @Override
  public void onMessage(String message) {
    if (messageConsumer != null) {
      messageConsumer.accept(message);
    }
  }

  @Override
  public void onMessage(ByteBuffer data) {
    try {
      Optional.ofNullable(dataConsumer).ifPresent(dataConsumer -> dataConsumer.accept(this, data));
    } finally {
      data.clear();
    }
  }

  @Override
  public void onError(Exception ex) {
    LOGGER.error("WS connection error: client id {}", clientId, ex);
  }
}