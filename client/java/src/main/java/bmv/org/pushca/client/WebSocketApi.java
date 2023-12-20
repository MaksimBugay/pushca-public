package bmv.org.pushca.client;

import bmv.org.pushca.client.model.WebSocketState;

public interface WebSocketApi {

  void send(byte[] data);

  void send(String text);

  void close();

  boolean isOpen();

  WebSocketState getWebSocketState();
}
