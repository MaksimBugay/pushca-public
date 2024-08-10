package bmv.pushca.binary.proxy.api;

import bmv.pushca.binary.proxy.pushca.exception.CannotDownloadBinaryChunkException;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ApiController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketPool.class);

  private final WebsocketPool websocketPool;
  private final BinaryProxyService binaryProxyService;

  @Autowired
  public ApiController(
      WebsocketPool websocketPool, BinaryProxyService binaryProxyService) {
    this.websocketPool = websocketPool;
    this.binaryProxyService = binaryProxyService;
  }

  @PostMapping(value = "/binary/admin/recreate-ws-pool")
  public Mono<Void> reCreateWebsocketPool() {
    return Mono.fromRunnable(() -> {
      websocketPool.closeNettyWebsocketPool();
      websocketPool.createNettyWebsocketPool();
    });
  }

  @GetMapping(value = "/binary/{workspaceId}/{binaryId}")
  public Flux<byte[]> serveBinaryAsStream(
      @PathVariable String workspaceId,
      @PathVariable String binaryId,
      ServerHttpResponse response) {
    final ConcurrentLinkedQueue<String> pendingChunks = new ConcurrentLinkedQueue<>();
    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .onErrorResume(throwable -> Mono.error(
            new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)))
        .flatMapMany(binaryManifest -> {
              // Set the Content-Disposition header to suggest the filename for the download
              response.getHeaders().setContentDisposition(ContentDisposition.builder("attachment")
                  .filename(binaryManifest.name())
                  .build());
              // Set the Content-Length header if the total size is known
              response.getHeaders()
                  .setContentLength(binaryManifest.getTotalSize()); // Assuming totalSize is available
              // Set the Content-Type header
              if (StringUtils.isNotEmpty(binaryManifest.mimeType())) {
                response.getHeaders().setContentType(MediaType.valueOf(binaryManifest.mimeType()));
              } else {
                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
              }
              return Flux.fromIterable(binaryManifest.datagrams())
                  .flatMap(
                      dtm -> Mono.fromFuture(
                              binaryProxyService.requestBinaryChunk(
                                  workspaceId,
                                  binaryManifest.downloadSessionId(),
                                  binaryId,
                                  dtm,
                                  binaryManifest.datagrams().size() - 1,
                                  pendingChunks)
                          )
                          .onErrorResume(throwable -> Mono.error(
                                  new CannotDownloadBinaryChunkException(
                                      binaryId, dtm,
                                      binaryManifest.downloadSessionId(),
                                      throwable
                                  )
                              )
                          )
                  );
            }
        )
        .onErrorResume(
            throwable -> {
              if (throwable instanceof CannotDownloadBinaryChunkException) {
                binaryProxyService.removeDownloadSession(
                    ((CannotDownloadBinaryChunkException) throwable).binaryId,
                    ((CannotDownloadBinaryChunkException) throwable).downloadSessionId
                );
                if ((throwable.getCause() != null)
                    && (throwable.getCause() instanceof TimeoutException)) {
                  LOGGER.error("Failed by timeout attempt to download binary with id {}",
                      binaryId, throwable);
                  response.setStatusCode(HttpStatus.NOT_FOUND);
                  return Mono.empty();
                } else {
                  return Mono.error(throwable);
                }
              } else {
                return Mono.error(new RuntimeException("Error fetching binary data", throwable));
              }
            })
        .doFinally((signalType) -> {
          pendingChunks.forEach(websocketPool::removeResponseWaiter);
          pendingChunks.clear();
        })
        /*.doOnCancel(() -> {
          System.out.println("!!!");
        })*/;
  }
}
