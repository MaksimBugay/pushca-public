package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.objectToBase64Binary;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.MANIFEST_KEY_CHUNK_INDEX;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.toDatagram;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.toDatagramPrefix;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import bmv.pushca.binary.proxy.pushca.SendBinaryAgent;
import bmv.pushca.binary.proxy.pushca.connection.model.BinaryType;
import bmv.pushca.binary.proxy.pushca.exception.SendBinaryError;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

public class BinaryStreamPublisher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryStreamPublisher.class);

  private final UUID binaryId;

  private final String name;

  private final String mimeType;

  private final PClient sender;

  private final String pusherInstanceId;

  private final SendBinaryAgent sendBinaryAgent;

  private final Map<Integer, Datagram> datagrams = new TreeMap<>();

  protected BinaryStreamPublisher(String id,
                                  String name,
                                  String mimeType,
                                  PClient sender,
                                  String pusherInstanceId,
                                  SendBinaryAgent sendBinaryAgent) {
    this.sendBinaryAgent = sendBinaryAgent;
    if (StringUtils.isBlank(id)) {
      binaryId = ID_GENERATOR.generate();
    } else {
      this.binaryId = UUID.fromString(id);
    }

    this.name = name;
    this.mimeType = mimeType;
    this.sender = new PClient(
        ID_GENERATOR.generate().toString(),
        sender.accountId(),
        sender.deviceId(),
        sender.applicationId()
    );
    this.pusherInstanceId = pusherInstanceId;
  }

  protected void processChunk(int index, byte[] chunk) throws SendBinaryError {
    int destHashCode = sender.hashCode();
    datagrams.put(index, toDatagram(index, chunk));
    byte[] prefix = toDatagramPrefix(
        BinaryType.CACHE_BINARY,
        destHashCode, false,
        binaryId, index
    );
    sendBinaryAgent.send(addAll(prefix, chunk));
  }

  protected void processChunk(long index, DataBuffer chunkDataBuffer) throws SendBinaryError {
    if (index < 0 || index > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Datagram Index must be between 0 and " + Integer.MAX_VALUE);
    }
    byte[] bytes = new byte[chunkDataBuffer.readableByteCount()];
    chunkDataBuffer.read(bytes);
    processChunk((int) index, bytes);
  }

  /**
   * Processes a chunk asynchronously, ensuring the data is written to the WebSocket channel
   * before the returned Mono completes.
   *
   * @param index the chunk index
   * @param chunk the chunk data
   * @return Mono that completes when the chunk has been sent
   */
  protected Mono<Void> processChunkAsync(int index, byte[] chunk) {
    int destHashCode = sender.hashCode();
    datagrams.put(index, toDatagram(index, chunk));
    byte[] prefix = toDatagramPrefix(
        BinaryType.CACHE_BINARY,
        destHashCode, false,
        binaryId, index
    );
    return sendBinaryAgent.sendAsync(addAll(prefix, chunk));
  }

  /**
   * Processes a chunk from a DataBuffer asynchronously, ensuring the data is written to the
   * WebSocket channel before the returned Mono completes.
   *
   * @param index the chunk index
   * @param chunkDataBuffer the chunk data buffer
   * @return Mono that completes when the chunk has been sent
   */
  protected Mono<Void> processChunkAsync(long index, DataBuffer chunkDataBuffer) {
    if (index < 0 || index > Integer.MAX_VALUE) {
      return Mono.error(new IllegalArgumentException(
          "Datagram Index must be between 0 and " + Integer.MAX_VALUE));
    }
    byte[] bytes = new byte[chunkDataBuffer.readableByteCount()];
    chunkDataBuffer.read(bytes);
    return processChunkAsync((int) index, bytes);
  }

  protected String uploadManifest() throws SendBinaryError {
    BinaryManifest manifest = new BinaryManifest(
        binaryId.toString(),
        name,
        mimeType,
        null,
        List.copyOf(datagrams.values()),
        null,
        sender,
        pusherInstanceId,
        null,
        null,
        Instant.now().getEpochSecond() + Duration.ofMinutes(15).getSeconds()
    );

    processChunk(MANIFEST_KEY_CHUNK_INDEX, objectToBase64Binary(manifest));

    datagrams.clear();

    return String.format("https://secure.fileshare.ovh/binary/%s/%s", manifest.sender().workSpaceId(), binaryId);
  }

  /**
   * Uploads the binary manifest asynchronously, ensuring the manifest is written to the WebSocket
   * channel before the returned Mono completes.
   *
   * @return Mono that emits the binary URL when the manifest has been sent
   */
  protected Mono<String> uploadManifestAsync() {
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
        Instant.now().getEpochSecond() + Duration.ofMinutes(15).getSeconds()
    );

    LOGGER.debug("Publish binary manifest: {}", manifest);

    datagrams.clear();

    String url = String.format("https://secure.fileshare.ovh/binary/%s/%s", manifest.sender().workSpaceId(), binaryId);

    return processChunkAsync(MANIFEST_KEY_CHUNK_INDEX, objectToBase64Binary(manifest))
        .thenReturn(url);
  }

  @Override
  public void close() {
    datagrams.clear();
  }
}
