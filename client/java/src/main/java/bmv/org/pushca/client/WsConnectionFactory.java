package bmv.org.pushca.client;

import java.net.URI;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;

public interface WsConnectionFactory {

  WebSocketApi createConnection(URI wsUrl, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext);
}
