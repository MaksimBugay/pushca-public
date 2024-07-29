package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.util.serialisation.TaskRunner.runWithDelay;

import bmv.pushca.binary.proxy.pushca.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.Datagram;
import bmv.pushca.binary.proxy.pushca.PClient;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class BinaryProxyService {

  private final WebsocketPool websocketPool;

  public BinaryProxyService(WebsocketPool websocketPool) {
    this.websocketPool = websocketPool;
  }

  public Flux<byte[]> getByteStream() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(this::generateChunk)
        .take(10);                       // Limit the number of chunks for this example
  }

  public CompletableFuture<BinaryManifest> requestBinaryManifest(String workspaceId,
      String binaryId) {
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
    runWithDelay(() -> websocketPool.completeWithBinaryManifest(manifest), 300L);

    return websocketPool.registerResponseFuture(
        binaryId,
        BinaryManifest.class
    );
  }

  public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String binaryId,
      int order) {

    return CompletableFuture.supplyAsync(() -> {
          try {
            Thread.sleep(/*order * */100L);
            return ("Chunk " + order).getBytes(StandardCharsets.UTF_8);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new byte[0];
          }
        }
    );
  }

  private byte[] generateChunk(long sequence) {
    return ("Chunk " + sequence).getBytes(StandardCharsets.UTF_8);
  }
}
