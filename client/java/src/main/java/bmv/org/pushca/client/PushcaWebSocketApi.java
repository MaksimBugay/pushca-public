package bmv.org.pushca.client;

import bmv.org.pushca.client.model.BinaryObjectMetadata;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface PushcaWebSocketApi {

  void processBinary(WebSocketApi ws, ByteBuffer byteBuffer,
      BiConsumer<WebSocketApi, byte[]> dataConsumer);

  void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<WebSocketApi, String> messageConsumer, Consumer<String> acknowledgeConsumer,
      Consumer<BinaryObjectMetadata> binaryManifestConsumer);

  void sendAcknowledge(String id);

  void sendMessageWithAcknowledge(String id, PClient dest, boolean preserveOrder,
      String message);

  void sendMessageWithAcknowledge(String id, PClient dest, String message);

  void sendMessage(String id, ClientFilter dest, boolean preserveOrder, String message);

  void sendMessage(ClientFilter dest, String message);

  void sendMessage(String id, PClient dest, boolean preserveOrder, String message);

  void sendMessage(PClient dest, String message);

  void sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge, boolean manifestOnly);

  void sendBinary(PClient dest, byte[] data, boolean withAcknowledge);

  void sendBinary(PClient dest, byte[] data);
}
