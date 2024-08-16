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

    CreatePrivateUrlSuffixRequest decryptedRequest = encryptionService.decrypt(
        urlSuffix.get(),
        CreatePrivateUrlSuffixRequest.class
    );
    assertEquals(request, decryptedRequest);
  }

  @Test
  void protectedBinaryTest() throws Exception {
    Thread.sleep(5000);
    final String workspaceId = "cec7abf69bab9f5aa793bd1c0c101e99";
    final String binaryId = "aba62189-9876-4001-9ba2-d3a80bd28f0c";
    String canPlayType = "probably";
    String mimeType = "video/mp4";
    DownloadProtectedBinaryRequest request = new DownloadProtectedBinaryRequest(
        encryptionService.encrypt(new CreatePrivateUrlSuffixRequest(workspaceId, binaryId)),
        1723826605070L,
        canPlayType,
        "m83CkAldIu2+ZQAH/KK4fpC1iYA5j7OziDbSx8OXAU0="
    );

    Flux<byte[]> responseBody = client.post().uri("/binary/protected")
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
    assertEquals(5736579, totalSize);
  }

  @Test
  void binaryProxyTest() throws Exception {
    Thread.sleep(5000);
    final String workspaceId = "cec7abf69bab9f5aa793bd1c0c101e99";
    final String binaryId = "aba62189-9876-4001-9ba2-d3a80bd28f0c";
    String mimeType = "video/mp4";
    Flux<byte[]> responseBody = client.get().uri(MessageFormat.format(
            "/binary/{0}/{1}?canPlayType=probably",
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
    assertEquals(5736579, totalSize);
  }
}
