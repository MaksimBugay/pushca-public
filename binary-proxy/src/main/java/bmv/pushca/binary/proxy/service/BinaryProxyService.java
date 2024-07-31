package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;

import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class BinaryProxyService {

  private final WebsocketPool websocketPool;

  public BinaryProxyService(WebsocketPool websocketPool) {
    this.websocketPool = websocketPool;
  }

  public CompletableFuture<BinaryManifest> requestBinaryManifest(String workspaceId,
      String binaryId) {
    CompletableFuture<BinaryManifest> future = websocketPool.registerResponseWaiter(
        binaryId
    );
    sendUploadBinaryAppeal(
        new ClientSearchData(
            workspaceId,
            null,
            null,
            "ultimate-file-sharing-listener"
        ),
        binaryId, DEFAULT_CHUNK_SIZE, true, null
    );

    return future;
  }

  public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String binaryId,
      Datagram datagram, boolean isLastChunk) {
    final ClientSearchData ownerFilter = new ClientSearchData(
        workspaceId,
        null,
        null,
        "ultimate-file-sharing-listener"
    );
    final String datagramId = buildDatagramId(binaryId, datagram.order());
    ResponseWaiter<byte[]> responseWaiter = new ResponseWaiter<>(
        (chunk) -> chunk.length == datagram.size() && calculateSha256(chunk).equals(datagram.md5()),
        (ex) -> sendUploadBinaryAppeal(
            ownerFilter, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order())
        ),
        MessageFormat.format("Invalid chunk {0} of binary with id {1} was received",
            String.valueOf(datagram.order()), binaryId)
    );
    websocketPool.registerResponseWaiter(
        datagramId, responseWaiter
    );

    if (isLastChunk) {
      sendUploadBinaryAppeal(
          ownerFilter, binaryId, DEFAULT_CHUNK_SIZE, false, null
      );
    }

    return responseWaiter;
  }

  private void sendUploadBinaryAppeal(ClientSearchData owner, String binaryId, int chunkSize,
      boolean manifestOnly, List<Integer> requestedChunks) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("owner", owner);
    metaData.put("binaryId", binaryId);
    metaData.put("chunkSize", chunkSize);
    metaData.put("manifestOnly", manifestOnly);
    metaData.put("requestedChunks", requestedChunks);

    websocketPool.sendCommand(null, SEND_UPLOAD_BINARY_APPEAL, metaData);
  }

  private synchronized void sendUploadBinaryAppeal(UploadBinaryAppeal appeal) {
    sendUploadBinaryAppeal(
        appeal.owner(),
        appeal.binaryId(),
        appeal.chunkSize(),
        appeal.manifestOnly(),
        appeal.requestedChunks()
    );
  }
}
