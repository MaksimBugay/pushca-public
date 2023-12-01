package bmv.org.pushcaverifier.client.rest;

import bmv.org.pushcaverifier.config.ConfigService;
import io.netty.channel.ChannelOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Service
public class PooledHttpWebClient {

  private final Map<Integer, WebClient> pool = new ConcurrentHashMap<>();

  public PooledHttpWebClient(ConfigService configService) {

    for (int i = 0; i < configService.getLoadTestRunnerNumber(); i++) {
      this.pool.put(i, createWebClient());
    }
  }

  public WebClient getWebClient(int index) {
    return pool.get(index);
  }

  public int getPoolSize() {
    return pool.size();
  }

  public static WebClient createWebClient() {
    final int size = 32 * 1024 * 1024;
    final ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
        .build();

    ConnectionProvider connectionProvider = ConnectionProvider.builder("myConnectionPool")
        .maxConnections(100)
        .pendingAcquireMaxCount(100)
        .build();
    ReactorClientHttpConnector clientHttpConnector = new ReactorClientHttpConnector(
        HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60_000)
            .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
    );

    return WebClient.builder()
        .defaultHeader(HttpHeaders.USER_AGENT, "BMV Servers: Pushca-verifier")
        .clientConnector(clientHttpConnector)
        .exchangeStrategies(strategies)
        .build();
  }
}
