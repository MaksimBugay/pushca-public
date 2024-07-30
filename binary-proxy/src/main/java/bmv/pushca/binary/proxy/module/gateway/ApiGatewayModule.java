package bmv.pushca.binary.proxy.module.gateway;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.internal.api.ApiGatewayResponseModule;
import bmv.pushca.binary.proxy.jms.TopicProducer;
import bmv.pushca.binary.proxy.model.ErrorObject;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsRoute;
import bmv.pushca.binary.proxy.model.command.Command;
import bmv.pushca.binary.proxy.model.query.Query;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ApiGatewayModule {

  private final ApiGatewayResponseModule apiGatewayResponseModule;

  private final TopicProducer topicProducer;

  private final BinaryProxyService binaryProxyService;

  private final JmsRoute apiGatewayResponseRoute;

  private final int responseTimeoutMs;

  @Autowired
  public ApiGatewayModule(
      MicroserviceConfiguration configuration,
      TopicProducer topicProducer,
      ApiGatewayResponseModule apiGatewayResponseModule,
      BinaryProxyService binaryProxyService, JmsRoute apiGatewayResponseRoute) {
    this.responseTimeoutMs = configuration.responseTimeoutMs;
    this.apiGatewayResponseModule = apiGatewayResponseModule;
    this.topicProducer = topicProducer;
    this.binaryProxyService = binaryProxyService;
    this.apiGatewayResponseRoute = apiGatewayResponseRoute;
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
                        binaryProxyService.requestBinaryChunk(workspaceId, binaryId, dtm.order())
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

  @PostMapping(value = "/query/{query}", consumes = APPLICATION_JSON_VALUE)
  Mono<String> fetchQueryResult(@PathVariable("query") String queryName,
      @RequestBody Map<String, Object> payload) {
    final InternalMessage internalMessage;
    try {
      internalMessage = Query.getQuery(queryName, toJson(payload), apiGatewayResponseRoute);
    } catch (IllegalArgumentException ex) {
      return Mono.just(toJson(ErrorObject.getInstance(ex)));
    }
    return Mono.fromFuture(topicProducer.sendMessages(internalMessage))
        .then(Mono.fromFuture(apiGatewayResponseModule.registerResponseFuture(internalMessage)))
        .map(resultPayload -> resultPayload)
        .timeout(Duration.ofMillis(responseTimeoutMs))
        .onErrorResume(ex -> Mono.just(toJson(ErrorObject.getInstance(ex))));
  }


  @PostMapping(value = "/command/{command}", consumes = APPLICATION_JSON_VALUE)
  Mono<String> executeCommand(@PathVariable("command") String commandName,
      @RequestBody Map<String, Object>[] payloads) {
    final InternalMessage[] messages;
    try {
      messages = Arrays.stream(payloads).map(
          payload -> Command.getCommand(commandName, toJson(payload), apiGatewayResponseRoute)
      ).toList().toArray(new InternalMessage[] {});
    } catch (IllegalArgumentException ex) {
      return Mono.just(toJson(ErrorObject.getInstance(ex)));
    }
    return Mono.fromFuture(topicProducer.sendMessages(messages))
        .thenMany(Flux.fromArray(messages))
        .flatMap(internalMessage -> Mono
            .fromFuture(apiGatewayResponseModule.registerResponseFuture(internalMessage)))
        .collectList()
        .map(list -> String.join(";", list))
        .timeout(Duration.ofMillis(responseTimeoutMs))
        .onErrorResume(ex -> Mono.just(toJson(ErrorObject.getInstance(ex))));
  }
}
