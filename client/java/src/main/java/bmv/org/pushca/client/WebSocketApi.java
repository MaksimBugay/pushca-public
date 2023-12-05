package bmv.org.pushca.client;

import bmv.org.pushca.client.model.ReadyState;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import org.java_websocket.handshake.ServerHandshake;

public interface WebSocketApi {

  void connect();

  void send(ByteBuffer bytes);

  void send(String text);

  void close();

  boolean isOpen();

  boolean isClosing();

  ReadyState getWebSocketState();

  void onOpen(ServerHandshake handshakeData);

  void onClose(int code, String reason, boolean remote);

  void onMessage(String message);

  void onMessage(ByteBuffer data);

  void onError(Exception ex);

  int getConnectTimeoutMs();

  BiConsumer<WebSocketApi, String> getMessageConsumer();

  BiConsumer<WebSocketApi, ByteBuffer> getDataConsumer();

  BiConsumer<Integer, String> getOnCloseListener();
}
