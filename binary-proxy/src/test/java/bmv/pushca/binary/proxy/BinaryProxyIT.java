package bmv.pushca.binary.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
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

  @DynamicPropertySource
  static void customProperties(DynamicPropertyRegistry registry) {
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
        "An_ehNRThW_r1o7tY8fBqG5HcvgmL6sj-cyqmsIceadrW2UgA2qYn6_-Gh7I-IkCIJphhWYIMrfgo4h2zTlYQFXjZV4wadjhEo5D2lQ1kfeXjBEm6s0fN4oNCAU3RleLLuLQhbIB0l27mkKeOugDYHfYIb6uLUrW5QOlAeUpB6tp38wdoa5KDoKv80rj0L79VSJOUQLXrgC5JDi3ZyITI-qQt_FE";

    EntityExchangeResult<String> response = client.get().uri(MessageFormat.format(
            "/binary-manifest/protected/{0}",
            suffix
        ))
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
    //85fb3881ad15bf9ae956cb30f22c5855/eb69bb0c-d49b-443d-ba2b-0ce9c6491092
    final String workspaceId = "85fb3881ad15bf9ae956cb30f22c5855";
    final String binaryId = "eb69bb0c-d49b-443d-ba2b-0ce9c6491092";

    EntityExchangeResult<String> response = client.get().uri(MessageFormat.format(
            "/binary-manifest/{0}/{1}",
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
  void binaryProxyTest() throws Exception {
    Thread.sleep(5000);
    //https://secure.fileshare.ovh:31443/binary/615abb482d4d7850eccbd0e693c5641b/f3bbfed4-56bd-40e0-8554-59f8224db3a4
    final String workspaceId = "615abb482d4d7850eccbd0e693c5641b";
    final String binaryId = "f3bbfed4-56bd-40e0-8554-59f8224db3a4";
    String mimeType = "video/mp4";
    Flux<byte[]> responseBody = client.get().uri(MessageFormat.format(
            "/binary/{0}/{1}",
            workspaceId,
            binaryId
        ))
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
    assertEquals(15673308, totalSize);
  }
}
