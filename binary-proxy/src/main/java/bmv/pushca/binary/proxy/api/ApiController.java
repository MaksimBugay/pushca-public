package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ApiController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketPool.class);

  private final BinaryProxyService binaryProxyService;
  private final int responseTimeoutMs;

  @Autowired
  public ApiController(
      MicroserviceConfiguration configuration,
      BinaryProxyService binaryProxyService) {
    this.responseTimeoutMs = configuration.responseTimeoutMs;
    this.binaryProxyService = binaryProxyService;
  }

  @GetMapping(value = "/binary/{workspaceId}/{binaryId}")
  public Flux<byte[]> serveBinaryAsStream(
      @PathVariable String workspaceId,
      @PathVariable String binaryId,
      @RequestParam(value = "mimeType", defaultValue = MediaType.APPLICATION_OCTET_STREAM_VALUE)
      String mimeType,
      ServerHttpResponse response) {
    response.getHeaders().setContentType(MediaType.valueOf(mimeType));

    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId)
            .orTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS))
        .onErrorResume(throwable -> Mono.error(
            new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)))
        .flatMapMany(binaryManifest -> Flux.fromIterable(binaryManifest.datagrams())
            .flatMap(
                dtm -> Mono.fromFuture(
                        binaryProxyService.requestBinaryChunk(
                            workspaceId,
                            binaryId,
                            dtm,
                            responseTimeoutMs)
                    ).doOnSuccess(bytes -> {
                      if (dtm.order() < (binaryManifest.datagrams().size() - 1)) {
                        binaryProxyService.sendUploadBinaryAppeal(
                            workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false,
                            List.of(dtm.order() + 1)
                        );
                      }
                    })
                    .onErrorResume(throwable -> Mono.error(
                        new RuntimeException(MessageFormat.format(
                            "Error fetching chunk: binary id {0}, order {1}",
                            binaryId, String.valueOf(dtm.order())), throwable)))
            )
        )
        .onErrorResume(
            throwable -> {
              if (throwable instanceof RuntimeException
                  && throwable.getCause() instanceof TimeoutException) {
                LOGGER.error("Failed attempt to download binary with id {}", binaryId, throwable);
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return Mono.empty();
              } else {
                return Mono.error(new RuntimeException("Error fetching binary data", throwable));
              }
            });
        /*.doOnCancel(() -> {

        });*/
  }
}
