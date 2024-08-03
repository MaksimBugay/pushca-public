package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class BinaryProxyService {

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
      String binaryId, Datagram datagram, int maxOrder) {
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

    responseWaiter.whenComplete((bytes, error) -> {
      if (error == null) {
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

    final String waiterId = concatParts(datagramId, downloadSessionId);
    websocketPool.registerDownloadSession(binaryId, downloadSessionId);
    websocketPool.registerResponseWaiter(
        waiterId, responseWaiter
    );

    if (datagram.order() == 0) {
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
