package bmv.pushca.binary.proxy.api;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ApiController {

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
  public Flux<byte[]> serveBinaryAsStream(@PathVariable String workspaceId,
      @PathVariable String binaryId,
      ServerHttpResponse response) {
    String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    response.getHeaders().setContentType(MediaType.valueOf(mimeType));

    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .flatMapMany(binaryManifest -> Flux.fromIterable(binaryManifest.datagrams())
            .flatMap(
                dtm -> Mono.fromFuture(
                        binaryProxyService.requestBinaryChunk(
                            workspaceId,
                            binaryId,
                            dtm.order(),
                            dtm.order() == (binaryManifest.datagrams().size() - 1))
                    )
                    .onErrorResume(throwable -> Mono.error(
                        new RuntimeException("Error fetching chunk: " + dtm.order(), throwable)))
                    .timeout(Duration.ofMillis(responseTimeoutMs))
            )
        )
        .onErrorResume(
            throwable -> Mono.error(new RuntimeException("Error fetching binary data", throwable)))
        .doOnCancel(() -> {

        });
  }
}
