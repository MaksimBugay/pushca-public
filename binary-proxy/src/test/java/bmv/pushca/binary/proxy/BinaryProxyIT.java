package bmv.pushca.binary.proxy;

import static bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory.BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID;
import static bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory.PUSHCA_CLUSTER_WORKSPACE_ID;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.api.request.GetPublicBinaryManifestRequest;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.IpGeoLookupService;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yaml")
@SpringBootTest(classes = {
    BinaryProxyMicroservice.class}, webEnvironment = RANDOM_PORT)
class BinaryProxyIT {

  static {
  }

  protected WebTestClient client;

  @Value("${spring.webflux.base-path:}")
  String contextPath;

  @LocalServerPort
  private String port;

  @Autowired
  private EncryptionService encryptionService;

  @Autowired
  private IpGeoLookupService ipGeoLookupService;

  @Autowired
  private BinaryProxyService binaryProxyService;

  @DynamicPropertySource
  static void customProperties(DynamicPropertyRegistry registry) {
    registry.add("binary-proxy.geo-lookup.db.path", () -> "C:\\tmp\\");
  }

  @BeforeEach
  public void prepare() {
    client = WebTestClient
        .bindToServer()
        .baseUrl("http://localhost:" + port + contextPath)
        .responseTimeout(Duration.of(1, ChronoUnit.MINUTES))
        .build();
  }

  @Test
  void ipResolverTest() {
    String ipAddress = "95.216.194.46";
    GeoLookupResponse response = ipGeoLookupService.resolve(ipAddress);
    assertEquals("FI", response.countryCode());
    assertEquals("Finland", response.countryName());
    assertEquals("Helsinki", response.city());
    assertEquals(
        "[type=PUB, ASN=24940, ISP=Hetzner Online GmbH, usage_type=DCH, threat=BOTNET]",
        response.proxyInfo()
    );
  }

  @Test
  void geoIpResolvingTest() {
    ResolveIpRequest request = new ResolveIpRequest("85.190.239.186");

    client.post()
        .uri("/binary/resolve-ip")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(GeoLookupResponse.class)
        .value(response -> {
          assertNotNull(response);
          System.out.println(response);
        });
  }

  @Test
  void resolveIpViaWsGatewayTest() throws InterruptedException {
    Thread.sleep(5000);
    ClientSearchData dest = new ClientSearchData(
        PUSHCA_CLUSTER_WORKSPACE_ID,
        "admin",
        null,
        BINARY_PROXY_CONNECTION_TO_PUSHER_APP_ID,
        true,
        null
    );

    ResolveIpRequest request = new ResolveIpRequest("95.216.194.46");
    byte[] requestPayload = toJson(request).getBytes(StandardCharsets.UTF_8);

    binaryProxyService.sendGatewayRequest(
        dest, false, "RESOLVE_IP_WITH_PROXY_CHECK", requestPayload
    );
    Thread.sleep(2000);
  }

  @Test
  void geoIpResolvingWithProxyCheckTest() {
    ResolveIpRequest request = new ResolveIpRequest("95.216.194.46");

    client.post()
        .uri("/binary/resolve-ip-with-proxy-check")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(GeoLookupResponse.class)
        .value(response -> {
          assertNotNull(response);
          assertEquals("FI", response.countryCode());
          assertEquals("Finland", response.countryName());
          assertEquals("Helsinki", response.city());
          assertEquals(
              "[type=PUB, ASN=24940, ISP=Hetzner Online GmbH, usage_type=DCH, threat=BOTNET]",
              response.proxyInfo()
          );
        });
  }

  @Test
  void createPrivateUrlSuffixTest() throws Exception {
    CreatePrivateUrlSuffixRequest request = new CreatePrivateUrlSuffixRequest(
        "cec7abf69bab9f5aa793bd1c0c101e99",
        "aba62189-9876-4001-9ba2-d3a80bd28f0c"
    );

    AtomicReference<String> urlSuffix = new AtomicReference<>();
    client.post()
        .uri("/binary/private/create-url-suffix")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(response -> {
          assertNotNull(response);
          urlSuffix.set(response);
        });
    String shortPrefix = urlSuffix.get().split("@@")[0];
    System.out.printf("%s: %d%n", shortPrefix, shortPrefix.length());
    CreatePrivateUrlSuffixRequest decryptedRequest = encryptionService.decrypt(
        urlSuffix.get().split("@@")[1],
        CreatePrivateUrlSuffixRequest.class
    );
    assertEquals(request, decryptedRequest);
  }

  @Test
  void protectedBinaryTest() throws Exception {
    Thread.sleep(5000);
    DownloadProtectedBinaryRequest request = new DownloadProtectedBinaryRequest(
        "AyhMxlDsvEBGSdmZLV_5GnNGDHV4QpIS2J9IIM9z7lGL7wLQ7QDZVqFhZ3ZKMP9Y_8wKii8pAonYxuGO6jSI6CE2DTuzUgL5k0pwWdzr0F4r1uTSya6LNGqM7DBpyyRfJFo8NtJgosOUXq0rjiypbj4OlAiaf4yUbsG5PL56V84QEOsaLJjZFoqoK86RgHl5xF5iq6KF5-1kLmJUJjFWWCApqElZ%7CeyJiYXNlNjRLZXkiOiJVTW1SZWdSWmhpNDJCcnFyNGpSSVpYcWF3REJ1a2RBSCt5MlhSMGN1TmVrT1cvNnRQL2pBaE45ZFNzNUJOdWNrIiwiYmFzZTY0SVYiOiJBTUxQSHBQdVd3TEVpZlI4In0",
        Instant.now().toEpochMilli() + 1000_000,
        "test",
        null,
        "pmWkWSBCL51Bfkhn79xPuKBKHz//H6B+mY6G9/eieuM="
    );
    String mimeType = MediaType.APPLICATION_OCTET_STREAM.getType();

    Flux<byte[]> responseBody = client.post()
        .uri("/binary/protected")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(mimeType)
        .returnResult(byte[].class)
        .getResponseBody();
    Map<Integer, byte[]> binary = new ConcurrentHashMap<>();
    AtomicInteger order = new AtomicInteger();
    StepVerifier.create(responseBody)
        .thenConsumeWhile(chunk -> {
          binary.put(order.getAndIncrement(), chunk);
          return true;
        })
        .verifyComplete();
    long totalSize = binary.values().stream()
        .map(chunk -> chunk.length)
        .reduce(Integer::sum).orElse(0);
    assertEquals(244211, totalSize);
  }

  @Test
  void getProtectedBinaryDescriptionTest() throws Exception {
    Thread.sleep(5000);
    final String suffix =
        "AiW9yPharNmluBXgCQm4oB2_9_IlmzW4NO3g0uI1M3hiiNadzANtWAmb2HBSKFWhga1km-8J13G17Wg58IBdFnUCIhvNqFtw0IP8NOH_o2hoeHg6LLjx5L_4OXEB5TssqvLUBaj4jhPcWxkPTKs_pdwmAdK4zrSvvs6AiotziiMxDlV1m0RIfGli7u7zETzO13r1Ek8_yaMyFHHR81PGv49PR3l3";

    String url = MessageFormat.format(
        "/binary/binary-manifest/protected/{0}",
        suffix
    );
    EntityExchangeResult<String> response = client.get().uri(url)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult();
    String readMeText = response.getResponseBody();
    System.out.println(readMeText);
  }

  @Test
  void getBinaryDescriptionTest() throws Exception {
    Thread.sleep(5000);
    //https://secure.fileshare.ovh:31443/binary/85fb3881ad15bf9ae956cb30f22c5855/6d9a9584-f4d9-4df7-9611-a8a9a651278b
    final String workspaceId = "85fb3881ad15bf9ae956cb30f22c5855";
    final String binaryId = "6d9a9584-f4d9-4df7-9611-a8a9a651278b";

    EntityExchangeResult<String> response = client.get().uri(MessageFormat.format(
            "/binary/binary-manifest/{0}/{1}",
            workspaceId,
            binaryId
        ))
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult();
    String readMeText = response.getResponseBody();
    System.out.println(readMeText);
  }

  @Test
  void getPublicBinaryManifestTest() throws Exception {
    Thread.sleep(5000);
    GetPublicBinaryManifestRequest request = new GetPublicBinaryManifestRequest(
        "1f92c8904b8a3a86a60bc9ddf66eff32",
        "74a4e642-d880-4b6f-b5ae-cf2edeff9a0e",
        "aea5c935-5aab-4b98-a35a-84de2da31b00",
        "YWVhNWM5MzUtNWFhYi00Yjk4LWEzNWEtODRkZTJkYTMxYjAwOksyNm9GeDh3NDdINUVudFgzVEJnd2M1YjZLYUdmdXJa"
        //null,//UUID.randomUUID().toString(),
        //null//UUID.randomUUID().toString()
    );

    BinaryManifest manifest = client.post()
        .uri("/binary/m/public")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .returnResult(BinaryManifest.class)
        .getResponseBody().blockFirst();

    System.out.println(manifest);
  }

  @Test
  void getBinaryManifestTest() throws Exception {
    Thread.sleep(5000);
    final String workspaceId = "85fb3881ad15bf9ae956cb30f22c5855";
    final String binaryId = "d7594d47-afc7-412b-b4f4-a88de5ffefdc";

    BinaryManifest manifest = client.get().uri(MessageFormat.format(
            "/binary/m/{0}/{1}",
            workspaceId,
            binaryId
        ))
        .exchange()
        .expectStatus().isOk()
        .returnResult(BinaryManifest.class)
        .getResponseBody().blockFirst();

    assertEquals("test.mp4", manifest.name());
    assertEquals("video/mp4", manifest.mimeType());
    assertEquals(9840497, manifest.getTotalSize());
  }

  @Test
  void binaryProxyTest() throws Exception {
    Thread.sleep(5000);
    //https://secure.fileshare.ovh/binary/85fb3881ad15bf9ae956cb30f22c5855/d7594d47-afc7-412b-b4f4-a88de5ffefdc
    final String workspaceId = "85fb3881ad15bf9ae956cb30f22c5855";
    final String binaryId = "d7594d47-afc7-412b-b4f4-a88de5ffefdc";
    String mimeType = "video/mp4";
    Flux<byte[]> responseBody = client.get().uri(MessageFormat.format(
            "/binary/{0}/{1}",
            workspaceId,
            binaryId
        ))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(mimeType)
        .expectHeader().value("X-Total-Size", v -> {
          if (!v.equals("9840497")) {
            throw new IllegalStateException("Wrong total size header");
          }
        })
        .returnResult(byte[].class)
        .getResponseBody();
    Map<Integer, byte[]> binary = new ConcurrentHashMap<>();
    AtomicInteger order = new AtomicInteger();
    StepVerifier.create(responseBody)
        .thenConsumeWhile(chunk -> {
          binary.put(order.getAndIncrement(), chunk);
          return true;
        })
        .verifyComplete();
    long totalSize = binary.values().stream()
        .map(chunk -> chunk.length)
        .reduce(Integer::sum).orElse(0);
    assertEquals(9840497, totalSize);
  }
}
