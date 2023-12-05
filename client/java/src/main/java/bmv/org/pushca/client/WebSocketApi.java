package bmv.org.pushca.client;

import bmv.org.pushca.client.model.ReadyState;
import java.nio.ByteBuffer;
import org.java_websocket.handshake.ServerHandshake;

public interface WebSocketApi {

  void connect();

  void send(ByteBuffer bytes);

  void send(String text);

  boolean isOpen();

  ReadyState getWebSocketState();

  void onOpen(ServerHandshake handshakeData);

  void onClose(int code, String reason, boolean remote);

  void onMessage(String message);

  void onMessage(ByteBuffer data);

  void onError(Exception ex);
}
