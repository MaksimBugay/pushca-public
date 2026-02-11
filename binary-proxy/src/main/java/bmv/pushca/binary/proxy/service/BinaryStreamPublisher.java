package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.objectToBase64Binary;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.MANIFEST_KEY_CHUNK_INDEX;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.toDatagram;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.toDatagramPrefix;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import bmv.pushca.binary.proxy.pushca.SendBinaryAgent;
import bmv.pushca.binary.proxy.pushca.connection.model.BinaryType;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.pushca.util.ThumbnailUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

public class BinaryStreamPublisher implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(BinaryStreamPublisher.class);

  private final String pusherInstanceId;

  private final AtomicReference<BinaryManifest> manifestHolder = new AtomicReference<>();

  private final Supplier<SendBinaryAgent> sendBinaryAgentSupplier;

  private final Map<Integer, Datagram> datagrams = new TreeMap<>();

  protected BinaryStreamPublisher(String pusherInstanceId,
                                  Supplier<SendBinaryAgent> sendBinaryAgentSupplier) {
    this.sendBinaryAgentSupplier = sendBinaryAgentSupplier;
    this.pusherInstanceId = pusherInstanceId;
  }

  public BinaryManifest getManifest() {
    return manifestHolder.get();
  }

  /**
   * Processes a chunk asynchronously, ensuring the data is written to the WebSocket channel
   * before the returned Mono completes.
   *
   * @param index the chunk index
   * @param chunk the chunk data
   * @return Mono that completes when the chunk has been sent
   */
  public Mono<Void> processChunkAsync(UUID binaryId, int index, byte[] chunk, boolean storeInManifest, PClient sender) {
    int destHashCode = sender.hashCode();
    if (storeInManifest) {
      datagrams.put(index, toDatagram(index, chunk));
    }
    byte[] prefix = toDatagramPrefix(
        BinaryType.CACHE_BINARY,
        destHashCode, false,
        binaryId, index
    );
    return sendBinaryAgentSupplier.get().sendAsync(addAll(prefix, chunk));
  }

  /**
   * Processes a chunk from a DataBuffer asynchronously, ensuring the data is written to the
   * WebSocket channel before the returned Mono completes.
   *
   * @param index           the chunk index
   * @param chunkDataBuffer the chunk data buffer
   * @return Mono that completes when the chunk has been sent
   */
  public Mono<Void> processChunkAsync(UUID binaryId, long index, DataBuffer chunkDataBuffer, boolean storeInManifest, PClient sender) {
    if (index < 0 || index > Integer.MAX_VALUE) {
      return Mono.error(new IllegalArgumentException(
          "Datagram Index must be between 0 and " + Integer.MAX_VALUE));
    }
    byte[] bytes = new byte[chunkDataBuffer.readableByteCount()];
    chunkDataBuffer.read(bytes);
    return processChunkAsync(binaryId, (int) index, bytes, storeInManifest, sender);
  }

  /**
   * Uploads the binary manifest asynchronously, ensuring the manifest is written to the WebSocket
   * channel before the returned Mono completes.
   *
   * @return Mono that emits the binary URL when the manifest has been sent
   */
  public Mono<String> uploadManifestAsync(UUID binaryId,
                                          String name,
                                          String mimeType,
                                          PClient sender,
                                          Function<BinaryStreamPublisher, Mono<Void>> beforeSendHook) {
    BinaryManifest manifest = new BinaryManifest(
        binaryId.toString(),
        name,
        mimeType,
        null,
        new ArrayList<>(datagrams.values()),
        null,
        sender,
        pusherInstanceId,
        null,
        null,
        Instant.now().getEpochSecond() + Duration.ofDays(1).getSeconds()
    );

    manifestHolder.set(manifest);

    LOGGER.debug("Publish binary manifest: {}", manifest);

    datagrams.clear();

    String url = "https://secure.fileshare.ovh/public-binary-ex.html?w=%s&id=%s&tn=%s".formatted(
        manifest.sender().workSpaceId(),
        manifest.id(),
        ThumbnailUtils.buildThumbnailId(manifest.id())
    );


    return ((beforeSendHook == null) ? Mono.empty() : beforeSendHook.apply(this))
        .then(
            Mono.defer(
                () -> uploadPreparedManifestAsync(manifest)
            )
        )
        .thenReturn(url);
  }

  public Mono<String> uploadPreparedManifestAsync(BinaryManifest manifest) {
    String url = String.format(
        "https://secure.fileshare.ovh/binary/%s/%s",
        manifest.sender().workSpaceId(),
        manifest.id()
    );
    return processChunkAsync(
        UUID.fromString(manifest.id()),
        MANIFEST_KEY_CHUNK_INDEX,
        objectToBase64Binary(manifest),
        false,
        manifest.sender()
    )
        .thenReturn(url);
  }

  @Override
  public void close() {
    datagrams.clear();
  }
}
