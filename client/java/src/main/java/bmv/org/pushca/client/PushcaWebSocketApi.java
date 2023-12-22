package bmv.org.pushca.client;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
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
   * processes an incoming text message from Pushca
   *
   * @param ws                     - websocket connection object
   * @param inMessage              - incoming text message
   * @param messageConsumer        - external handler of text messages
   * @param acknowledgeConsumer    - external handler of received acknowledges
   * @param binaryManifestConsumer - external handler of reveived binary manifests
   */
  void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectData> binaryManifestConsumer);

  /**
   * acknowledge Pushca about received message (Pushca forwards acknowledge to sender)
   *
   * @param id - message id
   */
  void sendAcknowledge(String id);

  /**
   * acknowledge Pushca about received datagram (Pushca forwards acknowledge to sender)
   *
   * @param binaryId - binary(file) id
   * @param order    - datagram order in binary
   */
  void sendAcknowledge(UUID binaryId, int order);

  /**
   * send message to some client and wait for acknowledge, if no acknowledge after defined number of
   * send attempts then throw exception
   *
   * @param id            - message id (if null then will be assigned by Pushca)
   * @param dest          - client who should receive a message
   * @param preserveOrder - keep sending order during delivery
   * @param message       - message text
   * @throws WebsocketConnectionIsBrokenException - failed delivery exception
   */
  void sendMessageWithAcknowledge(String id, PClient dest, boolean preserveOrder,
      String message) throws WebsocketConnectionIsBrokenException;

  /**
   * short version of sendMessageWithAcknowledge method, that uses defaults: preserveOrder = false
   *
   * @param id      - message id (if null then will be assigned by Pushca)
   * @param dest    - client who should receive a message
   * @param message - message text
   * @throws WebsocketConnectionIsBrokenException - failed delivery exception
   */
  void sendMessageWithAcknowledge(String id, PClient dest, String message)
      throws WebsocketConnectionIsBrokenException;

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
