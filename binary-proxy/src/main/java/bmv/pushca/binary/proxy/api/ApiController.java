package bmv.pushca.binary.proxy.api;

import bmv.pushca.binary.proxy.pushca.exception.CannotDownloadBinaryChunkException;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
      @RequestParam(value = "mimeType", defaultValue = MediaType.APPLICATION_OCTET_STREAM_VALUE)
      String mimeType,
      ServerHttpResponse response) {
    response.getHeaders().setContentType(MediaType.valueOf(mimeType));
    final ConcurrentLinkedQueue<String> pendingChunks = new ConcurrentLinkedQueue<>();
    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .onErrorResume(throwable -> Mono.error(
            new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)))
        .flatMapMany(binaryManifest -> Flux.fromIterable(binaryManifest.datagrams())
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
            )
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
                  LOGGER.error("Failed by timeout attempt to download binary with id {}", binaryId,
                      throwable);
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
