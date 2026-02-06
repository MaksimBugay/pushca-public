package bmv.pushca.binary.proxy.service;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.util.concurrent.TimeUnit;

public class WebClientFactory {

  public static WebClient createWebClient() {
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
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.MINUTES))
                )
    );

    return WebClient.builder()
        .defaultHeader(HttpHeaders.USER_AGENT, "Binary proxy: pushca download url processor")
        .clientConnector(clientHttpConnector)
        .exchangeStrategies(strategies)
        .build();
  }
}
