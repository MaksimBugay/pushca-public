package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.api.request.PublishRemoteStreamRequest;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.pushca.util.ThumbnailUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;
import static bmv.pushca.binary.proxy.pushca.connection.NettyWsClient.DEFAULT_SEND_BUFFER_SIZE;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.pushca.util.SendBinaryHelper.toDatagram;

@Service
public class PublishBinaryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PublishBinaryService.class);

  private final WebsocketPool websocketPool;

  private final PushcaWsClientFactory pushcaWsClientFactory;

  private final WebClient webClient = WebClientFactory.createWebClient();

  public PublishBinaryService(WebsocketPool websocketPool, PushcaWsClientFactory pushcaWsClientFactory) {
    this.websocketPool = websocketPool;
    this.pushcaWsClientFactory = pushcaWsClientFactory;
  }

  public Mono<String> publishRemoteStreamThumbnail(String serverBaseUrl, String remoteStreamUrl, BinaryStreamPublisher publisher) {
    if (StringUtils.isEmpty(serverBaseUrl)) {
      return Mono.error(new IllegalArgumentException("serverBaseUrl must be a non-empty string"));
    }
    if (StringUtils.isEmpty(remoteStreamUrl)) {
      return Mono.error(new IllegalArgumentException("remoteStreamUrl must be a non-empty string"));
    }

    // Normalize base URL (remove trailing slashes)
    String normalizedBase = serverBaseUrl.replaceAll("/+$", "");
    String downloadUrl = normalizedBase + "/download/thumbnail";

    String binaryId = publisher.getManifest().id();

    return webClient.post()
        .uri(downloadUrl)
        .body(BodyInserters.fromValue(new PublishRemoteStreamRequest(remoteStreamUrl)))
        .exchangeToMono(
            response -> {
              String mimeType = response.headers().contentType()
                  .map(MediaType::toString)
                  .orElse("image/png");

              return response.bodyToMono(byte[].class)
                  .flatMap(
                      thumbnailBytes -> publishRemoteStreamThumbnail(binaryId, mimeType, thumbnailBytes, publisher)
                  );
            }
        );
  }

  private Mono<String> publishRemoteStreamThumbnail(
      String binaryId,
      String mimeType,
      byte[] thumbnailBytes,
      BinaryStreamPublisher publisher) {
    BinaryManifest manifest = publisher.getManifest();

    PClient sender = new PClient(
        ThumbnailUtils.THUMBNAIL_WORKSPACE_ID,
        pushcaWsClientFactory.pushcaClient.accountId(),
        pushcaWsClientFactory.pushcaClient.deviceId(),
        pushcaWsClientFactory.pushcaClient.applicationId()
    );

    UUID thumbnailId = ThumbnailUtils.buildThumbnailId(binaryId);

    return publisher.processChunkAsync(thumbnailId, 0, thumbnailBytes, false, sender)
        .then(
            publisher.uploadPreparedManifestAsync(
                new BinaryManifest(
                    thumbnailId.toString(),
                    ThumbnailUtils.buildThumbnailName(binaryId, mimeType),
                    mimeType,
                    null,
                    List.of(toDatagram(0, thumbnailBytes)),
                    manifest.senderIP(),
                    sender,
                    manifest.pusherInstanceId(),
                    manifest.downloadSessionId(),
                    null,
                    manifest.expireAt()
                )
            )
        )
        .doOnSuccess(
            url -> LOGGER.debug("Thumbnail uploaded successfully: url={}, mimeType={}", url, mimeType)
        );
  }

  /**
   * Downloads a remote stream in chunks and publishes it via WebSocket.
   *
   * <p>This method downloads content from the specified remote URL, splits it into chunks,
   * and sends each chunk through a WebSocket connection. After all chunks are sent,
   * a binary manifest is uploaded to finalize the transfer.</p>
   *
   * <p>Backpressure: This method applies backpressure with a bounded buffer (256 chunks).
   * If the WebSocket cannot keep up, an error will be signaled after the buffer is full.</p>
   *
   * <p>Timeouts: Each chunk has a 30-second processing timeout. The overall stream
   * has a 30-minute timeout to prevent indefinite hanging on slow streams.</p>
   *
   * <p>Resource management: DataBuffers are automatically released after processing.
   * The BinaryStreamPublisher is closed in all cases (success, error, or cancellation).</p>
   *
   * @param serverBaseUrl   the base URL of the download server
   * @param remoteStreamUrl the URL of the remote stream to download
   * @param chunkSize       the target size of each chunk in bytes (uses default if &lt;= 0)
   * @param chunkLimit      optional maximum number of chunks to download; if {@code null} or &lt;= 0,
   *                        the entire stream is downloaded; otherwise downloading stops after this many
   *                        chunks and the manifest is uploaded with whatever was received
   * @return a Mono that completes with the upload manifest result when the stream is fully processed
   * @throws IllegalArgumentException if serverBaseUrl or remoteStreamUrl is empty
   */
  public Mono<String> publishRemoteStream(String serverBaseUrl, String remoteStreamUrl, int chunkSize, Integer chunkLimit) {
    if (StringUtils.isEmpty(serverBaseUrl)) {
      return Mono.error(new IllegalArgumentException("serverBaseUrl must be a non-empty string"));
    }
    if (StringUtils.isEmpty(remoteStreamUrl)) {
      return Mono.error(new IllegalArgumentException("remoteStreamUrl must be a non-empty string"));
    }

    final int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;

    // Normalize base URL (remove trailing slashes)
    String normalizedBase = serverBaseUrl.replaceAll("/+$", "");
    String downloadUrl = normalizedBase + "/download";

    return webClient.post()
        .uri(downloadUrl)
        .body(BodyInserters.fromValue(new PublishRemoteStreamRequest(remoteStreamUrl)))
        .exchangeToFlux(
            response -> {
              if (!response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("Unknown error")
                    .flatMapMany(errorText -> Flux.error(new RuntimeException(
                        MessageFormat.format("Download failed: {0} - {1}",
                            response.statusCode(), errorText))));
              }

              final UUID binaryId = ID_GENERATOR.generate();
              final String filename = extractFilename(response.headers().asHttpHeaders(), remoteStreamUrl);
              final String mediaType = extractMediaType(response.headers().asHttpHeaders(), filename);
              final PClient sender = new PClient(
                  ID_GENERATOR.generate().toString(),
                  pushcaWsClientFactory.pushcaClient.accountId(),
                  pushcaWsClientFactory.pushcaClient.deviceId(),
                  pushcaWsClientFactory.pushcaClient.applicationId()
              );
              @SuppressWarnings("resource") final BinaryStreamPublisher publisher = createBinaryStreamPublisher();

              // Create a chunker that buffers DataBuffer slices by byte size
              DataBufferChunker chunker = new DataBufferChunker(effectiveChunkSize);

              return response.bodyToFlux(DataBuffer.class)
                  // Limit demand to the HTTP client so Netty does not buffer the entire response.
                  // Request one DataBuffer at a time (replenish when 0 pending).
                  .limitRate(128, 0)
                  .doOnNext(buf -> LOGGER.debug("Received DataBuffer: {} bytes", buf.readableByteCount()))
                  .doOnComplete(() -> LOGGER.debug("HTTP response body completed"))
                  .doOnError(e -> LOGGER.error("HTTP response error", e))
                  // Buffer by actual byte size instead of item count
                  .transformDeferred(
                      flux -> flux
                          .concatMapIterable(chunker::add)
                          .concatWith(Mono.fromSupplier(chunker::flush)
                              .filter(Objects::nonNull))
                  )
                  .doOnNext(chunk -> LOGGER.debug("Chunk produced: {} bytes", chunk.readableByteCount()))
                  .doFinally(
                      signalType -> {
                        LOGGER.debug("Chunker doFinally: signal={}", signalType);
                        chunker.discard();
                      }
                  )
                  .index()
                  // If chunkLimit is specified, take only that many chunks then complete
                  // (cancels upstream HTTP download and proceeds to manifest upload)
                  .transform(flux -> chunkLimit != null && chunkLimit > 0
                      ? flux.take(chunkLimit)
                      : flux)
                  // Apply backpressure with bounded buffer to prevent memory issues
                  .onBackpressureBuffer(
                      DEFAULT_SEND_BUFFER_SIZE,
                      dropped -> LOGGER.warn("Chunk {} dropped due to backpressure", dropped.getT1()),
                      BufferOverflowStrategy.ERROR
                  )
                  .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                  .doOnDiscard(
                      Tuple2.class,
                      tuple -> {
                        Object second = tuple.getT2();
                        if (second instanceof DataBuffer db) {
                          DataBufferUtils.release(db);
                        }
                      }
                  )
                  // Process each chunk with per-chunk timeout, waiting for actual WebSocket write
                  .concatMap(
                      indexed -> {
                        DataBuffer chunk = indexed.getT2();
                        return publisher.processChunkAsync(binaryId, indexed.getT1(), chunk, true, sender)
                            .doFinally(signal -> DataBufferUtils.release(chunk))
                            .timeout(Duration.ofSeconds(30))
                            .onErrorMap(
                                TimeoutException.class,
                                e -> new RuntimeException(
                                    MessageFormat.format("Chunk send timeout at chunk {0}", indexed.getT1()),
                                    e
                                )
                            )
                            .doOnError(
                                error -> LOGGER.error(
                                    "Error during chunk processing: file name = {}, index = {}, error type = {}",
                                    filename,
                                    indexed.getT1(),
                                    error.getClass().getName(),
                                    error
                                )
                            )
                            .doOnSuccess(v -> LOGGER.debug("Chunk {} sent successfully", indexed.getT1()))
                            .thenReturn(indexed);
                      }
                  )
                  // Add overall timeout to prevent indefinite hanging on slow streams
                  .timeout(Duration.ofMinutes(30))
                  .doOnComplete(() -> LOGGER.debug("All chunks sent, uploading manifest"))
                  .doOnError(e -> LOGGER.error("Stream error before manifest upload", e))
                  // Defer so uploadManifestAsync() runs only after this Flux completes (i.e. after
                  // all chunks are processed), not at assembly time when datagrams are still empty
                  .then(
                      Mono.defer(
                          () -> publisher.uploadManifestAsync(
                              binaryId,
                              filename,
                              mediaType,
                              sender,
                              p -> publishRemoteStreamThumbnail(serverBaseUrl, remoteStreamUrl, p).then()
                          )
                      )
                  )
                  .doFinally(
                      ignoredSignal -> {
                        LOGGER.debug("Publisher doFinally: signal={}", ignoredSignal);
                        try {
                          publisher.close();
                        } catch (Exception e) {
                          LOGGER.warn("Error closing publisher", e);
                        }
                      }
                  )
                  .flux();
            })
        .next();
  }

  private String extractFilename(HttpHeaders headers, String sourceUrl) {
    // Try to get filename from Content-Disposition header
    String contentDisposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
    if (contentDisposition != null && !contentDisposition.isEmpty()) {
      // Parse filename from Content-Disposition: attachment; filename="example.pdf"
      String[] parts = contentDisposition.split(";");
      for (String part : parts) {
        String trimmed = part.trim();
        if (trimmed.toLowerCase().startsWith("filename=")) {
          String filename = trimmed.substring(9).trim();
          // Remove surrounding quotes if present
          if (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
          }
          if (filename.startsWith("'") && filename.endsWith("'")) {
            filename = filename.substring(1, filename.length() - 1);
          }
          return filename;
        }
      }
    }

    // Fall back to extracting filename from URL
    try {
      String path = java.net.URI.create(sourceUrl).getPath();
      if (StringUtils.isNotEmpty(path)) {
        String[] segments = path.split("/");
        if (segments.length > 0) {
          String lastSegment = segments[segments.length - 1];
          if (StringUtils.isNotEmpty(lastSegment)) {
            return java.net.URLDecoder.decode(lastSegment, StandardCharsets.UTF_8);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to extract filename from URL: {}", sourceUrl, e);
    }

    return "downloaded_file";
  }

  private String extractMediaType(HttpHeaders headers, String filename) {
    // Try to get media type from X-Content-Type custom header
    String mediaType = headers.getFirst("X-Content-Type");
    if (mediaType != null && !mediaType.isEmpty()) {
      return mediaType;
    }

    // Fall back to Content-Type header
    String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
    if (contentType != null && !contentType.isEmpty()) {
      // Remove charset or other parameters: "video/mp4; charset=utf-8" -> "video/mp4"
      int semicolonIndex = contentType.indexOf(';');
      if (semicolonIndex > 0) {
        contentType = contentType.substring(0, semicolonIndex).trim();
      }
      return contentType;
    }

    // Fall back to guessing from filename extension
    if (filename != null && filename.contains(".")) {
      String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
      return switch (extension) {
        case "mp4" -> "video/mp4";
        case "webm" -> "video/webm";
        case "mkv" -> "video/x-matroska";
        case "mp3" -> "audio/mpeg";
        case "m4a" -> "audio/mp4";
        case "ogg" -> "audio/ogg";
        default -> "application/octet-stream";
      };
    }

    return "application/octet-stream";
  }

  private BinaryStreamPublisher createBinaryStreamPublisher() {
    return new BinaryStreamPublisher(
        pushcaWsClientFactory.getPusherInstanceId(),
        websocketPool::getConnection
    );
  }

  /**
   * Helper class that accumulates DataBuffer slices and emits chunks by byte size.
   * <p>
   * For single-buffer chunks (common case), returns the buffer directly (zero-copy).
   * For multi-buffer chunks, uses {@link DataBufferUtils#join} to combine them.
   * </p>
   */
  private static class DataBufferChunker {
    private final int targetSize;
    private int currentSize;
    private List<DataBuffer> currentBuffers = new ArrayList<>();

    DataBufferChunker(int targetSize) {
      this.targetSize = targetSize;
    }

    List<DataBuffer> add(DataBuffer dataBuffer) {
      List<DataBuffer> chunks = new ArrayList<>();
      int remaining = dataBuffer.readableByteCount();

      while (remaining > 0) {
        int need = targetSize - currentSize;
        if (remaining <= need) {
          DataBufferUtils.retain(dataBuffer);
          currentBuffers.add(dataBuffer);
          currentSize += remaining;
          remaining = 0;
        } else {
          int splitIndex = dataBuffer.readPosition() + need;
          DataBuffer head = dataBuffer.split(splitIndex);
          DataBufferUtils.retain(head);
          currentBuffers.add(head);
          currentSize += need;
          remaining = dataBuffer.readableByteCount();
        }

        if (currentSize == targetSize) {
          chunks.add(joinBuffers(currentBuffers));
          currentBuffers = new ArrayList<>();
          currentSize = 0;
        }
      }

      DataBufferUtils.release(dataBuffer);
      return chunks;
    }

    DataBuffer flush() {
      if (currentSize > 0) {
        DataBuffer chunk = joinBuffers(currentBuffers);
        currentBuffers = new ArrayList<>();
        currentSize = 0;
        return chunk;
      }
      return null;
    }

    void discard() {
      for (DataBuffer buffer : currentBuffers) {
        DataBufferUtils.release(buffer);
      }
      currentBuffers = new ArrayList<>();
      currentSize = 0;
    }

    /**
     * Joins a list of DataBuffers into a single DataBuffer.
     * For single-buffer case, returns it directly (zero-copy).
     * For multiple buffers, synchronously copies into a new buffer.
     */
    private static DataBuffer joinBuffers(List<DataBuffer> buffers) {
      if (buffers.size() == 1) {
        // Zero-copy: return the single buffer directly
        return buffers.get(0);
      }
      // Synchronous join: allocate combined buffer and copy all data
      int totalSize = 0;
      for (DataBuffer buffer : buffers) {
        totalSize += buffer.readableByteCount();
      }
      DataBuffer result = buffers.get(0).factory().allocateBuffer(totalSize);
      for (DataBuffer buffer : buffers) {
        result.write(buffer);
        DataBufferUtils.release(buffer);
      }
      return result;
    }
  }
}
