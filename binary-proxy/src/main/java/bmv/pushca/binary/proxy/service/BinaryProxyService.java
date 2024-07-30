package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;

import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class BinaryProxyService {

  private final WebsocketPool websocketPool;

  public BinaryProxyService(WebsocketPool websocketPool) {
    this.websocketPool = websocketPool;
  }

  public CompletableFuture<BinaryManifest> requestBinaryManifest(String workspaceId,
      String binaryId) {
    CompletableFuture<BinaryManifest> future = websocketPool.registerResponseFuture(
        binaryId,
        BinaryManifest.class
    );
    websocketPool.sendUploadBinaryAppeal(
        new ClientSearchData(
            workspaceId,
            null,
            null,
            "ultimate-file-sharing-listener"
        ),
        binaryId, DEFAULT_CHUNK_SIZE, true, null
    );

    BinaryManifest manifest = new BinaryManifest(
        binaryId, "test",
        MediaType.APPLICATION_OCTET_STREAM_VALUE,
        List.of(
            new Datagram(0, "", "", 0),
            new Datagram(0, "", "", 1),
            new Datagram(0, "", "", 2),
            new Datagram(0, "", "", 3),
            new Datagram(0, "", "", 4)
        ),
        null, null
    );
    websocketPool.runWithDelay(() -> websocketPool.completeWithResponse(manifest.id(), manifest),
        300L);

    return future;
  }

  public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String binaryId,
      int order) {
    final String datagramId = buildDatagramId(binaryId, order);
    final String previousDatagramId = (order == 0) ? null : buildDatagramId(binaryId, order - 1);
    CompletableFuture<byte[]> future = websocketPool.registerResponseFuture(
        datagramId,
        byte[].class
    );

    websocketPool.sendUploadBinaryAppeal(
        new ClientSearchData(
            workspaceId,
            null,
            null,
            "ultimate-file-sharing-listener"
        ),
        binaryId, DEFAULT_CHUNK_SIZE, false, List.of(order)
    );

    websocketPool.runWithDelay(() -> websocketPool.completeWithResponse(
            datagramId,
            previousDatagramId,
            ("Chunk " + order).getBytes(StandardCharsets.UTF_8)
        ),
        (10 - order) * 100L);

    return future;
  }
}
