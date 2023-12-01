package bmv.org.pushcaverifier.client;


import bmv.org.pushcaverifier.client.rest.PusherAddress;
import bmv.org.pushcaverifier.client.rest.request.OpenConnectionPoolRequest;
import bmv.org.pushcaverifier.client.rest.request.OpenConnectionRequest;
import bmv.org.pushcaverifier.client.rest.response.OpenConnectionPoolResponse;
import bmv.org.pushcaverifier.client.rest.response.OpenConnectionResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OpenConnectionFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenConnectionFactory.class);

  private final AdvertisedUrlExtractor advertisedUrlExtractor;

  private final RestTemplate restTemplate;

  public OpenConnectionFactory(AdvertisedUrlExtractor advertisedUrlExtractor,
      RestTemplate restTemplate) {
    this.advertisedUrlExtractor = advertisedUrlExtractor;
    this.restTemplate = restTemplate;
  }

  public SimpleClient openConnection(String pushcaBaseUrl, PClientWithPusherId client) {
    return openConnection(pushcaBaseUrl, client, null, null, null, null);
  }

  public SimpleClient openConnection(String pushcaBaseUrl, PClientWithPusherId client, String pusherId,
      BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener) {
    ResponseEntity<OpenConnectionResponse> response = restTemplate
        .exchange(
            pushcaBaseUrl + "/open-connection", HttpMethod.POST,
            new HttpEntity<>(new OpenConnectionRequest(client, pusherId)),
            OpenConnectionResponse.class);
    if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
      throw new IllegalStateException(
          "Impossible to get metadata for a new connection, status code " + response.getStatusCode()
              .value());
    }

    String urlStr = advertisedUrlExtractor.getAdvertisedUrl(
        new PusherAddress(
            response.getBody().externalAdvertisedUrl(),
            response.getBody().internalAdvertisedUrl()
        )
    );

    SimpleClient wsClient = createSimpleClient(
        client,
        pusherId,
        urlStr,
        messageConsumer,
        dataConsumer,
        afterCloseListener
    );
    wsClient.connect();

    return wsClient;
  }

  public List<SimpleClient> openConnectionPool(
      String pushcaBaseUrl,
      PClientWithPusherId client, String pusherInstanceId, int poolSize,
      BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener) {
    ResponseEntity<OpenConnectionPoolResponse> response = restTemplate
        .exchange(
            pushcaBaseUrl + "/open-connection-pool", HttpMethod.POST,
            new HttpEntity<>(new OpenConnectionPoolRequest(client, pusherInstanceId, poolSize)),
            OpenConnectionPoolResponse.class);
    OpenConnectionPoolResponse openConnectionPoolResponse = response.getBody();
    if (!response.getStatusCode().is2xxSuccessful() || openConnectionPoolResponse == null) {
      throw new IllegalStateException(
          "Impossible to get metadata for a new connection pool, status code "
              + response.getStatusCode()
              .value());
    }
    String pusherId = openConnectionPoolResponse.pusherInstanceId();
    List<SimpleClient> pool = openConnectionPoolResponse.addresses().stream()
        .map(address -> createSimpleClient(client, pusherId, address, messageConsumer,
            dataConsumer, afterCloseListener))
        .toList();

    for (SimpleClient simpleClient : pool) {
      simpleClient.connect();
    }
    return pool;
  }

  private SimpleClient createSimpleClient(PClientWithPusherId client, String pusherId,
      String externalAdvertisedUrl,
      BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener) {
    try {
      return new SimpleClient(
          new URI(externalAdvertisedUrl),
          client.accountId(),
          (int) Duration.ofSeconds(30).toMillis(),
          pusherId,
          messageConsumer,
          dataConsumer,
          afterCloseListener);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private SimpleClient createSimpleClient(PClientWithPusherId client, String pusherId,
      PusherAddress address,
      BiConsumer<String, String> messageConsumer,
      BiConsumer<SimpleClient, ByteBuffer> dataConsumer,
      BiConsumer<Integer, String> afterCloseListener) {
    String urlStr = advertisedUrlExtractor.getAdvertisedUrl(address);
    return createSimpleClient(client, pusherId, urlStr, messageConsumer,
        dataConsumer, afterCloseListener);
  }

  public static void delay(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
