package bmv.pushca.binary.proxy.pushca.connection;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolRequest;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolResponse;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Component
public class PushcaWsClientFactory {

  public static final String PUSHCA_CLUSTER_WORKSPACE_ID = "PushcaCluster";

  public static final String BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID =
      "BINARY-PROXY-CONNECTION-TO-PUSHER";

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWsClientFactory.class);
  private final WebClient webClient;
  private final PushcaConfig pushcaConfig;
  private final MicroserviceConfiguration microserviceConfiguration;

  public PushcaWsClientFactory(PushcaConfig pushcaConfig,
      MicroserviceConfiguration microserviceConfiguration) {
    this.pushcaConfig = pushcaConfig;
    this.microserviceConfiguration = microserviceConfiguration;
    this.webClient = initWebClient();
  }

  public List<PushcaWsClient> createConnectionPool(int poolSize, String pusherInstanceId,
      Consumer<String> messageConsumer,
      BiConsumer<PushcaWsClient, ByteBuffer> dataConsumer,
      Consumer<PushcaWsClient> afterOpenListener,
      BiConsumer<PushcaWsClient, Integer> afterCloseListener) {
    final PClient client = new PClient(
        PUSHCA_CLUSTER_WORKSPACE_ID,
        "admin",
        microserviceConfiguration.getInstanceId(),
        BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID
    );
    OpenConnectionPoolResponse openConnectionPoolResponse = webClient.post()
        .uri(pushcaConfig.getPushcaClusterUrl() + "/open-connection-pool")
        .body(Mono.just(new OpenConnectionPoolRequest(client, pusherInstanceId, poolSize)),
            OpenConnectionPoolResponse.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(clientResponse -> {
          if (!HttpStatus.OK.equals(clientResponse.statusCode())) {
            throw new IllegalStateException(
                "Failed attempt to open internal ws connections pool" + toJson(client));
          } else {
            return clientResponse.bodyToMono(OpenConnectionPoolResponse.class);
          }
        }).block();
    if (openConnectionPoolResponse == null || CollectionUtils.isEmpty(
        openConnectionPoolResponse.addresses())) {
      throw new IllegalStateException(
          "Failed attempt to open internal ws connections pool(empty response) " + toJson(client));
    }
    AtomicInteger counter = new AtomicInteger();
    return openConnectionPoolResponse.addresses().stream()
        .map(address -> {
          try {
            return new PushcaWsClient(
                new URI(address.externalAdvertisedUrl()),
                MessageFormat.format("{0}_{1}", client.accountId(), counter.incrementAndGet()),
                Math.toIntExact(Duration.ofMinutes(5).toMillis()),
                messageConsumer,
                dataConsumer,
                afterOpenListener,
                afterCloseListener,
                pushcaConfig.getSslContext()
            );
          } catch (URISyntaxException e) {
            throw new RuntimeException(e);
          }
        })
        .toList();
  }

  private WebClient initWebClient() {
    final int size = 32 * 1024 * 1024;
    final ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
        .build();

    ConnectionProvider connectionProvider = ConnectionProvider.builder(
            "BinaryProxyPushcaConnectionPool"
        )
        .maxConnections(100)
        .pendingAcquireMaxCount(1000)
        .build();
    ReactorClientHttpConnector clientHttpConnector = new ReactorClientHttpConnector(
        HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60_000)
            .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
    );

    return WebClient.builder()
        .defaultHeader(HttpHeaders.USER_AGENT, "Binary proxy: pushca download url processor")
        .clientConnector(clientHttpConnector)
        .exchangeStrategies(strategies)
        .build();
  }
}
