package bmv.org.pushcaverifier.client;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class SimpleClient extends WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger("PushServerClient");
  private final String clientId;

  private final String pusherId;

  private final BiConsumer<String, String> messageConsumer;

  private final BiConsumer<SimpleClient, ByteBuffer> dataConsumer;

  private final BiConsumer<Integer, String> afterCloseListener;

  public SimpleClient(URI serverUri, Draft draft, String clientId,
      String pusherId, BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener) {
    super(serverUri, draft);
    this.clientId = clientId;
    this.pusherId = pusherId;
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.afterCloseListener = afterCloseListener;
  }

  public SimpleClient(URI serverUri,
      String clientId,
      int connectTimeoutMs,
      String pusherId,
      BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener, SSLContext sslContext) {
    super(serverUri, new Draft_6455(), Map.of("client-id", clientId),
        connectTimeoutMs);
    if (sslContext != null) {
      this.setSocketFactory(sslContext.getSocketFactory());
    }
    this.clientId = clientId;
    this.pusherId = pusherId;
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.afterCloseListener = afterCloseListener;
  }

  public String getPusherId() {
    return pusherId;
  }

  @Override
  public void onOpen(ServerHandshake handshakedata) {
    //LOGGER.info("new connection opened, client id = {}", clientId);
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    LOGGER.info("Client {} closed with exit code {}, additional info: {}", clientId, code, reason);
    Optional.ofNullable(afterCloseListener)
        .ifPresent(afterCloseListener -> afterCloseListener.accept(code, reason));
  }

  @Override
  public void onMessage(String message) {
    if (!StringUtils.hasText(message)) {
      return;
    }
    if (messageConsumer != null) {
      messageConsumer.accept(clientId, message);
    }
    LOGGER.debug("Client {} received message: {}", clientId, message);
  }

  @Override
  public void onMessage(ByteBuffer data) {
    Optional.ofNullable(dataConsumer).ifPresent(dataConsumer -> dataConsumer.accept(
        this, data));
    LOGGER.debug("Client {} received ByteBuffer", clientId);
  }

  @Override
  public void onError(Exception ex) {
    LOGGER.error("An error occurred, clientId {}", clientId, ex);
  }
}