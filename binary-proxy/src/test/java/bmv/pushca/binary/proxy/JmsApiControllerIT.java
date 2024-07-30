package bmv.pushca.binary.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class JmsApiControllerIT {

  static {
  }

  protected WebTestClient client;

  @Value("${spring.webflux.base-path:}")
  String contextPath;

  @LocalServerPort
  private String port;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
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
}
