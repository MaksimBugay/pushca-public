package bmv.org.pushca.client;

import bmv.org.pushca.client.model.WebSocketState;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaWebSocket extends WebSocketClient implements WebSocketApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaWebSocket.class);
  private final BiConsumer<WebSocketApi, String> messageConsumer;
  private final BiConsumer<WebSocketApi, byte[]> dataConsumer;
  private final BiConsumer<Integer, String> onCloseListener;

  public JavaWebSocket(URI wsUrl, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext) {
    super(wsUrl, new Draft_6455(), new HashMap<>(), connectTimeoutMs);
    if (sslContext != null) {
      this.setSocketFactory(sslContext.getSocketFactory());
    }
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.onCloseListener = onCloseListener;
  }

  @Override
  public WebSocketState getWebSocketState() {
    return WebSocketState.valueOf(getReadyState().name());
  }

  @Override
  public void onOpen(ServerHandshake handshakeData) {
    LOGGER.info("Web socket connection was open: url {}", this.uri.toString());
  }

  @Override
  public void onMessage(String message) {
    Optional.ofNullable(messageConsumer).ifPresent(mc -> mc.accept(this, message));
  }

  @Override
  public void onMessage(ByteBuffer data) {
    try {
      byte[] binary = data.array();
      Optional.ofNullable(dataConsumer).ifPresent(dc -> dc.accept(this, binary));
    } finally {
      ((Buffer) data).clear();
    }
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    Optional.ofNullable(onCloseListener).ifPresent(l -> l.accept(code, reason));
  }

  @Override
  public void onError(Exception ex) {
    LOGGER.error("Unexpected error", ex);
  }
}
