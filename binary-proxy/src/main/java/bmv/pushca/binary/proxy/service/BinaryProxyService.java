package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.PRIVATE_URL_SUFFIX;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_GATEWAY_REQUEST;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_MESSAGE;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;

import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

@Service
public class BinaryProxyService {

  private static final ClientSearchData BROADCAST_ALL_FILTER =
      new ClientSearchData(null, null, null, "ultimate-file-sharing-listener");
  private final WebsocketPool websocketPool;

  private final int pushcaClientHashCode;

  private final MicroserviceConfiguration microserviceConfiguration;

  public BinaryProxyService(WebsocketPool websocketPool,
      PushcaWsClientFactory pushcaWsClientFactory,
      MicroserviceConfiguration microserviceConfiguration) {
    this.pushcaClientHashCode = pushcaWsClientFactory.pushcaClient.hashCode();
    this.websocketPool = websocketPool;
    this.microserviceConfiguration = microserviceConfiguration;
  }

  public CompletableFuture<Boolean> verifyBinarySignature(
      ClientSearchData ownerFilter, DownloadProtectedBinaryRequest signedRequest) {

    String id = sendVerifySignatureRequest(ownerFilter, signedRequest);
    return websocketPool.registerResponseWaiter(
        id, microserviceConfiguration.responseTimeoutMs
    );
  }

  public CompletableFuture<String> getPrivateUrlSuffix(String binaryId) {
    String id = broadcastMessage(BROADCAST_ALL_FILTER, MessageFormat.format("{0}{1}{2}",
        PRIVATE_URL_SUFFIX.name(),
        MESSAGE_PARTS_DELIMITER,
        binaryId
    ));
    return websocketPool.registerResponseWaiter(
        id, microserviceConfiguration.responseTimeoutMs
    );
  }

  public CompletableFuture<BinaryManifest> requestBinaryManifest(String workspaceId,
      String binaryId) {
    CompletableFuture<BinaryManifest> future = websocketPool.registerResponseWaiter(
        binaryId, microserviceConfiguration.responseTimeoutMs
    );
    sendUploadBinaryAppeal(
        workspaceId, binaryId, DEFAULT_CHUNK_SIZE, true, null
    );

    return future;
  }

  public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String downloadSessionId,
      String binaryId, Datagram datagram, int maxOrder,
      ConcurrentLinkedQueue<String> pendingChunks) {
    final String datagramId = buildDatagramId(binaryId, datagram.order(), pushcaClientHashCode);
    ResponseWaiter<byte[]> responseWaiter = new ResponseWaiter<>(
        (chunk) -> chunk.length == datagram.size()
            && calculateSha256(chunk).equals(datagram.md5()),
        null,
        null,
        MessageFormat.format("Invalid chunk {0} of binary with id {1} was received",
            String.valueOf(datagram.order()), binaryId),
        () -> sendUploadBinaryAppeal(
            workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order())
        ),
        microserviceConfiguration.responseTimeoutMs,
        (maxOrder + 1L) * microserviceConfiguration.responseTimeoutMs
    );

    final String waiterId = concatParts(datagramId, downloadSessionId);

    responseWaiter.whenComplete((bytes, error) -> {
      if (error == null) {
        pendingChunks.remove(waiterId);
        if (datagram.order() < maxOrder) {
          final String nextDatagramId =
              buildDatagramId(binaryId, datagram.order() + 1, pushcaClientHashCode);
          websocketPool.activateResponseWaiter(concatParts(nextDatagramId, downloadSessionId));
          sendUploadBinaryAppeal(
              workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order() + 1)
          );
        } else {
          websocketPool.removeDownloadSession(binaryId, downloadSessionId);
        }
      }
    });

    websocketPool.registerResponseWaiter(
        waiterId, responseWaiter
    );
    pendingChunks.add(waiterId);

    if (datagram.order() == 0) {
      websocketPool.registerDownloadSession(binaryId, downloadSessionId);
      websocketPool.activateResponseWaiter(waiterId);
      sendUploadBinaryAppeal(
          workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(0)
      );
    }

    return responseWaiter;
  }

  public void removeDownloadSession(String binaryId, String sessionId) {
    websocketPool.removeDownloadSession(binaryId, sessionId);
  }

  public String sendVerifySignatureRequest(ClientSearchData dest,
      DownloadProtectedBinaryRequest signedRequest) {
    return sendGatewayRequest(
        dest,
        false,
        "verify-binary-signature",
        JsonUtility.toJsonAsBytes(signedRequest)
    );
  }

  public String broadcastMessage(ClientSearchData dest, String message) {
    String id = ID_GENERATOR.generate().toString();

    Map<String, Object> metaData = new HashMap<>();
    metaData.put("filter", dest);
    metaData.put("message",
        MessageFormat.format("{0}{1}{2}", id, MESSAGE_PARTS_DELIMITER, message));
    metaData.put("preserveOrder", false);

    return websocketPool.sendCommand(id, SEND_MESSAGE, metaData);
  }

  public String sendGatewayRequest(ClientSearchData dest,
      boolean preserveOrder, String path, byte[] requestPayload) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("receiver", dest);
    metaData.put("preserveOrder", preserveOrder);
    metaData.put("path", path);
    byte[] payload = requestPayload == null ? new byte[0] : requestPayload;
    metaData.put("payload", Base64.getEncoder().encodeToString(payload));

    return websocketPool.sendCommand(null, SEND_GATEWAY_REQUEST, metaData);
  }

  public void sendUploadBinaryAppeal(String workspaceId, String binaryId, int chunkSize,
      boolean manifestOnly, List<Integer> requestedChunks) {
    final ClientSearchData ownerFilter = new ClientSearchData(
        workspaceId,
        null,
        null,
        "ultimate-file-sharing-listener"
    );
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("owner", ownerFilter);
    metaData.put("binaryId", binaryId);
    metaData.put("chunkSize", chunkSize);
    metaData.put("manifestOnly", manifestOnly);
    metaData.put("requestedChunks", requestedChunks);

    websocketPool.sendCommand(null, SEND_UPLOAD_BINARY_APPEAL, metaData);
  }

}
