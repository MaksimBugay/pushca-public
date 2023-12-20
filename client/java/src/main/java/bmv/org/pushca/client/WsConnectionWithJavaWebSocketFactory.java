package bmv.org.pushca.client;

import java.net.URI;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;

public class WsConnectionWithJavaWebSocketFactory implements WsConnectionFactory {

  @Override
  public WebSocketApi createConnection(URI wsUrl, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer,
      BiConsumer<Integer, String> onCloseListener, SSLContext sslContext) {
    JavaWebSocket ws = new JavaWebSocket(wsUrl,
        connectTimeoutMs,
        messageConsumer,
        dataConsumer,
        onCloseListener,
        sslContext);
    ws.connect();
    return ws;
  }
}
