package bmv.org.pushca.client;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface PushcaWebSocketApi {

  void processBinary(WebSocketApi ws, ByteBuffer byteBuffer,
      BiConsumer<WebSocketApi, Binary> dataConsumer,
      BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer);

  void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectData> binaryManifestConsumer);

  void sendAcknowledge(String id);

  void sendAcknowledge(UUID binaryId, int order);

  void sendMessageWithAcknowledge(String id, PClient dest, boolean preserveOrder,
      String message);

  void sendMessageWithAcknowledge(String id, PClient dest, String message);

  void sendMessage(String id, ClientFilter dest, boolean preserveOrder, String message);

  void sendMessage(ClientFilter dest, String message);

  void sendMessage(String id, PClient dest, boolean preserveOrder, String message);

  void sendMessage(PClient dest, String message);

  void sendBinaryMessage(PClient dest, byte[] message, UUID id, boolean withAcknowledge);

  void sendBinaryMessage(PClient dest, byte[] message);

  void sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge, boolean manifestOnly);

  void sendBinary(PClient dest, byte[] data, boolean withAcknowledge);

  void sendBinary(PClient dest, byte[] data);
}
