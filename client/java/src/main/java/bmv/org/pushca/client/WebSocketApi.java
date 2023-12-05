package bmv.org.pushca.client;

import java.nio.ByteBuffer;
import org.java_websocket.handshake.ServerHandshake;

public interface WebSocketApi {

  void connect();

  void send(ByteBuffer bytes);

  void send(String text);

  boolean isOpen();

  void onOpen(ServerHandshake handshakeData);

  void onClose(int code, String reason, boolean remote);

  void onMessage(String message);

  void onMessage(ByteBuffer data);

  void onError(Exception ex);
}
