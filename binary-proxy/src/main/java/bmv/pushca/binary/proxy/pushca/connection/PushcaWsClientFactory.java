package bmv.pushca.binary.proxy.pushca.connection;

import static bmv.pushca.binary.proxy.pushca.connection.NettyWsClient.CLUSTER_SECRET_HEADER_NAME;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolRequest;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolResponse;
import bmv.pushca.binary.proxy.pushca.connection.model.PusherAddress;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.service.WebClientFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Component
public class PushcaWsClientFactory {

  public static final String PUSHCA_CLUSTER_WORKSPACE_ID = "PushcaCluster";

  public static final String BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID =
      "BINARY-PROXY-CONNECTION-TO-PUSHER";

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWsClientFactory.class);

  public final PClient pushcaClient;
  private final WebClient webClient;
  private final PushcaConfig pushcaConfig;
  private final MicroserviceConfiguration microserviceConfiguration;

  public PushcaWsClientFactory(PushcaConfig pushcaConfig,
      MicroserviceConfiguration microserviceConfiguration) {
    this.microserviceConfiguration = microserviceConfiguration;
    this.pushcaConfig = pushcaConfig;
    this.webClient = WebClientFactory.createWebClient();
    this.pushcaClient = new PClient(
        PUSHCA_CLUSTER_WORKSPACE_ID,
        "admin",
        microserviceConfiguration.getInstanceId(),
        BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID
    );
  }

  public Mono<List<NettyWsClient>> createNettyConnectionPool(int poolSize,
      String pusherInstanceId,
      Function<PusherAddress, String> wsAuthorizedUrlExtractor,
      Consumer<String> messageConsumer,
      BiConsumer<NettyWsClient, byte[]> dataConsumer,
      Consumer<NettyWsClient> afterOpenListener,
      Consumer<NettyWsClient> afterCloseListener,
      Scheduler scheduler) {
    LOGGER.info("Instance IP: {}", microserviceConfiguration.getInstanceIP());
    return webClient.post()
        .uri(pushcaConfig.getPushcaClusterUrl() + "/open-connection-pool")
        .header("X-Real-IP", microserviceConfiguration.getInstanceIP())
        .header(CLUSTER_SECRET_HEADER_NAME, pushcaConfig.getPushcaClusterSecret())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(new OpenConnectionPoolRequest(pushcaClient, pusherInstanceId, poolSize)),
            OpenConnectionPoolRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(clientResponse -> {
          if (!HttpStatus.OK.equals(clientResponse.statusCode())) {
            return Mono.error(new IllegalStateException(
                "Failed attempt to open internal ws connections pool" + toJson(pushcaClient)));
          } else {
            return clientResponse.bodyToMono(OpenConnectionPoolResponse.class);
          }
        })
        .flatMap(openConnectionPoolResponse -> {
          if (openConnectionPoolResponse == null || CollectionUtils.isEmpty(
              openConnectionPoolResponse.addresses())) {
            return Mono.error(new IllegalStateException(
                "Failed attempt to open internal ws connections pool(empty response) " + toJson(
                    pushcaClient)));
          }
          AtomicInteger indexInPool = new AtomicInteger(0);
          return Flux.fromIterable(openConnectionPoolResponse.addresses())
              .<NettyWsClient>handle((address, sink) -> {
                try {
                  sink.next(new NettyWsClient(
                      indexInPool.getAndIncrement(),
                      microserviceConfiguration.getInstanceIP(),
                      pushcaConfig.getPushcaClusterSecret(),
                      new URI(wsAuthorizedUrlExtractor.apply(address)),
                      messageConsumer,
                      dataConsumer,
                      afterOpenListener,
                      afterCloseListener,
                      pushcaConfig.getNettySslContext(),
                      scheduler
                  ));
                } catch (URISyntaxException e) {
                  sink.error(new RuntimeException(e));
                }
              })
              .collectList();
        })
        .subscribeOn(scheduler)
        .onErrorResume(throwable -> {
          LOGGER.error("Failed attempt to get authorized websocket urls from Pushca", throwable);
          return Mono.empty();
        })
        .timeout(Duration.ofSeconds(30));
  }
}
