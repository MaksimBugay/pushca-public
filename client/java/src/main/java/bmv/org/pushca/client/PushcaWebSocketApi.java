package bmv.org.pushca.client;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UnknownDatagram;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface PushcaWebSocketApi {

  /**
   * processes an incoming binary message from Pushca
   *
   * @param ws                      - websocket connection object
   * @param binary                  - incoming binary message
   * @param dataConsumer            - external handler of completed binaries
   * @param unknownDatagramConsumer - external handler of datagrams without manifest (usually used
   *                                for torrents like protocol implementation)
   * @param binaryMessageConsumer   - external handler of binary messages
   */
  void processBinary(WebSocketApi ws, byte[] binary,
      BiConsumer<WebSocketApi, Binary> dataConsumer,
      BiConsumer<WebSocketApi, UnknownDatagram> unknownDatagramConsumer,
      BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer);

  /**
   * processes a WebSocket message received from Pushca.
   *
   * @param ws
   * @param inMessage
   * @param messageConsumer
   * @param acknowledgeConsumer
   * @param binaryManifestConsumer
   */
  void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectData> binaryManifestConsumer);

  void sendAcknowledge(String id);

  void sendAcknowledge(UUID binaryId, int order);

  void sendMessageWithAcknowledge(String id, PClient dest, boolean preserveOrder,
      String message);

  void sendMessageWithAcknowledge(String id, PClient dest, String message);

  void BroadcastMessage(String id, ClientFilter dest, boolean preserveOrder, String message);

  /**
   * Send message to all connected clients that pass filter
   *
   * @param dest    - filter of receivers
   * @param message - message text
   */
  void BroadcastMessage(ClientFilter dest, String message);

  void sendMessage(String id, PClient dest, boolean preserveOrder, String message);

  void sendMessage(PClient dest, String message);

  void sendBinaryMessage(PClient dest, byte[] message, UUID id, boolean withAcknowledge);

  void sendBinaryMessage(PClient dest, byte[] message);

  BinaryObjectData sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge, boolean manifestOnly);

  void sendBinary(PClient dest, byte[] data, boolean withAcknowledge);

  void sendBinary(PClient dest, byte[] data);

  void sendBinary(BinaryObjectData binaryObjectData, boolean withAcknowledge,
      List<String> requestedIds);
}
