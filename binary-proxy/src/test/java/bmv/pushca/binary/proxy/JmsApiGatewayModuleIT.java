package bmv.pushca.binary.proxy;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import bmv.pushca.binary.proxy.model.ErrorObject;
import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import bmv.pushca.binary.proxy.module.gateway.ApiGatewayResponseModuleImpl;
import bmv.pushca.binary.proxy.module.monitor.MonitoringModuleImpl;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yaml")
@SpringBootTest(classes = {
    BinaryProxyMicroservice.class}, webEnvironment = RANDOM_PORT)
class JmsApiGatewayModuleIT {

  static KafkaContainer kafkaContainer;

  static {
    kafkaContainer =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
    kafkaContainer.start();
    Assertions.assertTrue(kafkaContainer.isRunning());
  }

  protected WebTestClient client;

  @Value("${spring.webflux.base-path:}")
  String contextPath;

  @LocalServerPort
  private String port;

  @Autowired
  private ApiGatewayResponseModuleImpl apiGatewayResponseModuleImpl;

  @Autowired
  private MonitoringModuleImpl monitoringModule;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
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
  void binaryProxyTest() {
    final String workspaceId = "cec7abf69bab9f5aa793bd1c0c101e99";
    final String binaryId = "a9be54a5-5203-4fa6-9515-eda2341f5890";
    Flux<byte[]> responseBody = client.get().uri(MessageFormat.format(
            "/binary/{0}/{1}",
            workspaceId,
            binaryId
        ))
        .exchange()
        .expectStatus().isOk()
        .returnResult(byte[].class)
        .getResponseBody();

    StepVerifier.create(responseBody)
        .consumeNextWith(
            chunk -> assertThat(new String(chunk, StandardCharsets.UTF_8)).startsWith("Chunk 0"))
        .consumeNextWith(
            chunk -> assertThat(new String(chunk, StandardCharsets.UTF_8)).startsWith("Chunk 1"))
        .consumeNextWith(
            chunk -> assertThat(new String(chunk, StandardCharsets.UTF_8)).startsWith("Chunk 2"))
        .thenConsumeWhile(chunk -> true)
        .verifyComplete();
  }


  @Test
  void apiGatewayResponseTimeoutTest() throws InterruptedException {
    ErrorObject errorObject = initBrowserProfiles(
        ErrorObject.class,
        new BrowserProfile(null, null, "ProfileNoMetadata", null, null)
    ).get(0);
    assertNotNull(errorObject);
    assertEquals(1, errorObject.code());
    assertEquals("No Response from server in defined timeout interval", errorObject.message());
    Thread.sleep(100);
    assertTrue(apiGatewayResponseModuleImpl.isWaitingHallEmpty());
  }

  @Test
  void apiGatewayErrorResponseTest() {
    ErrorObject errorObject = initBrowserProfiles(
        ErrorObject.class,
        new BrowserProfile(null, null, null, "Some metadata", null)
    ).get(0);
    assertNotNull(errorObject);
    assertEquals(0, errorObject.code());
    assertEquals("Name of browser profile cannot be empty", errorObject.message());

    errorObject = initBrowserProfiles(
        ErrorObject.class,
        Map.of("key", "value")
    );
    assertNotNull(errorObject);
    assertEquals(0, errorObject.code());
    assertTrue(errorObject.message()
        .startsWith("Cannot create command InitBrowserProfileCmd from payload"));
  }


  @Test
  void initBrowserProfileTest() {
    List<BrowserProfile> initiatedProfiles = initBrowserProfiles(
        BrowserProfile.class,
        new BrowserProfile(null, null, "test-profile1", "Some metadata", Boolean.TRUE),
        new BrowserProfile(null, null, "test-profile2", "Some metadata", Boolean.TRUE),
        new BrowserProfile(null, null, "test-profile3", "Some metadata", Boolean.TRUE)
    ).stream().sorted(Comparator.comparing(BrowserProfile::name)).collect(Collectors.toList());
    assertEquals(3, initiatedProfiles.size());
    for (int i = 1; i < 4; i++) {
      BrowserProfile initiatedProfile = initiatedProfiles.get(i - 1);
      assertEquals("test-profile" + i, initiatedProfile.name());
      assertNotNull(initiatedProfile.id());
      assertNotNull(initiatedProfile.uuid());
      assertEquals("Some metadata", initiatedProfile.metaData());

      assertTrue(monitoringModule.isRegistered(initiatedProfile.uuid()));
    }
  }

  @Test
  void getBrowserProfileTest() {
    BrowserProfile initiatedProfile = initBrowserProfiles(
        BrowserProfile.class,
        new BrowserProfile(null, null, "test-profile", "Some metadata", true)
    ).get(0);
    assertNotNull(initiatedProfile);
    BrowserProfile getProfileResult =
        getBrowserProfile(BrowserProfile.class, initiatedProfile.uuid());
    assertNotNull(getProfileResult);
    assertEquals(initiatedProfile.name(), getProfileResult.name());
    assertEquals(initiatedProfile.id(), getProfileResult.id());
    assertEquals(initiatedProfile.uuid(), getProfileResult.uuid());
    assertEquals(initiatedProfile.metaData(), getProfileResult.metaData());
    assertTrue(initiatedProfile.persistent());
  }

  @Nullable
  private <T> T getBrowserProfile(Class<T> tClass, UUID id) {
    return client.post()
        .uri("/query/get-browser-profile")
        .body(Mono.just(new BrowserProfile(null, id, null, null, null)),
            BrowserProfile.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.OK.value())
        .returnResult(tClass).getResponseBody().blockFirst();
  }

  private <T> List<T> initBrowserProfiles(Class<T> tClass, BrowserProfile... browserProfiles) {
    String rawResponse = client.post()
        .uri("/command/init-browser-profile")
        .body(Mono.just(browserProfiles), BrowserProfile[].class)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.OK.value())
        .returnResult(String.class).getResponseBody().blockFirst();

    assertNotNull(rawResponse);
    return Arrays.stream(rawResponse.split(";"))
        .map(responseItem -> fromJson(responseItem, tClass))
        .collect(Collectors.toList());
  }

  @Nullable
  private <T> T initBrowserProfiles(Class<T> tClass, Map<String, Object>... browserProfiles) {
    return client.post()
        .uri("/command/init-browser-profile")
        .body(Mono.just(browserProfiles), Map[].class)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.OK.value())
        .returnResult(tClass).getResponseBody().blockFirst();
  }
}
