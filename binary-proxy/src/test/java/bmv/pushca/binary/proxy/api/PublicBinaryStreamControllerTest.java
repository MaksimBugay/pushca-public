package bmv.pushca.binary.proxy.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.PublicBinaryChunkService;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PublicBinaryStreamControllerTest {
  private final BinaryProxyService service = mock(BinaryProxyService.class);
  private final WebsocketPool pool = mock(WebsocketPool.class);
  private final PublicBinaryChunkService chunkService = mock(PublicBinaryChunkService.class);
  private final PublicBinaryStreamController controller = new PublicBinaryStreamController(service, chunkService, pool);
  private final List<byte[]> data = List.of(bytes("abcd"), bytes("efghi"), bytes("jkl"));
  // Deliberately unsorted, with unequal chunk sizes.
  private final BinaryManifest manifest = new BinaryManifest("video", "video.mp4", "video/mp4", null,
      List.of(new Datagram(3, "", "", 2), new Datagram(4, "", "", 0), new Datagram(5, "", "", 1)),
      null, null);
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    when(service.requestBinaryManifestWithHumanOnlyCheck(eq("workspace"), eq("video"),
        any(), any(), any())).thenReturn(Mono.just(manifest));
    when(chunkService.requestBinaryChunk(eq("workspace"), anyString(), eq("video"), any(), any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(
            data.get(invocation.<Datagram>getArgument(3).order())));
    client = WebTestClient.bindToController(controller,
        new ApiController(pool, service, null, null, null, null, null)).build();
  }

  @ParameterizedTest
  @CsvSource({
      "bytes=5-10, fghijk, 5-10, 1:2",
      "bytes=4-, efghijkl, 4-11, 1:2",
      "bytes=-3, jkl, 9-11, 2",
      "bytes=4-8, efghi, 4-8, 1",
      "bytes=0-0, a, 0-0, 0",
      "bytes=11-100, l, 11-11, 2",
      "bytes=-100, abcdefghijkl, 0-11, 0:1:2"
  })
  void returnsOnlyRequestedBytesAndFetchesOnlyOverlappingChunks(
      String range, String body, String bounds, String orders) {
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, range).exchange()
        .expectStatus().isEqualTo(206)
        .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
        .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes " + bounds + "/12")
        .expectHeader().contentLength(body.length())
        .expectHeader().contentType("video/mp4")
        .expectBody(byte[].class).isEqualTo(bytes(body));

    ArgumentCaptor<Datagram> chunks = ArgumentCaptor.forClass(Datagram.class);
    verify(chunkService, times(orders.split(":").length))
        .requestBinaryChunk(eq("workspace"), anyString(), eq("video"), chunks.capture(), any());
    assertEquals(orders, String.join(":", chunks.getAllValues().stream()
        .map(chunk -> String.valueOf(chunk.order())).toList()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/binary/workspace/video", "/binary/workspace/video/video.mp4"})
  void legacyPublicEndpointsStillReturnTheFullBinary(String path) {
    when(service.requestBinaryChunk(eq("workspace"), anyString(), eq("video"), any(), anyInt(), any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(
            data.get(invocation.<Datagram>getArgument(3).order())));
    client.get().uri(path).header(HttpHeaders.RANGE, "bytes=6-7")
        .exchange().expectStatus().isOk()
        .expectHeader().doesNotExist(HttpHeaders.ACCEPT_RANGES)
        .expectHeader().doesNotExist(HttpHeaders.CONTENT_RANGE)
        .expectHeader().contentLength(12)
        .expectBody(byte[].class).isEqualTo(bytes("abcdefghijkl"));
    verifyNoInteractions(chunkService);
  }

  @Test
  void humanOnlyValidationStillAppliesToRangeRequests() {
    when(service.requestBinaryManifestWithHumanOnlyCheck(eq("workspace"), eq("video"),
        eq("page"), eq("token"), any())).thenAnswer(invocation -> {
          invocation.<java.util.function.Consumer<org.springframework.http.HttpStatus>>getArgument(4)
              .accept(org.springframework.http.HttpStatus.FORBIDDEN);
          return Mono.empty();
        });
    client.get().uri("/binary/stream/workspace/video/video.mp4?page-id=page&human-token=token")
        .header(HttpHeaders.RANGE, "bytes=6-7").exchange().expectStatus().isForbidden()
        .expectBody().isEmpty();
    verifyNoInteractions(chunkService);
  }

  @Test
  void fullDownloadPreservesByteOrder() {
    client.get().uri("/binary/stream/workspace/video/video.mp4").exchange().expectStatus().isOk()
        .expectHeader().contentLength(12)
        .expectHeader().doesNotExist(HttpHeaders.CONTENT_RANGE)
        .expectBody(byte[].class).isEqualTo(bytes("abcdefghijkl"));
  }

  @Test
  void forDownloadOnlyForcesAttachmentLikeTheLegacyNamedEndpoint() {
    client.get().uri("/binary/stream/workspace/video/video.mp4?for-download-only=true")
        .header(HttpHeaders.RANGE, "bytes=6-7").exchange()
        .expectStatus().isEqualTo(206)
        .expectHeader().contentDisposition(
            org.springframework.http.ContentDisposition.attachment().filename("video.mp4").build())
        .expectHeader().contentType("video/mp4")
        .expectBody(byte[].class).isEqualTo(bytes("gh"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bytes=12-", "bytes=5-3", "bytes=-0", "bytes=oops"})
  void rejectsInvalidOrUnsatisfiableRangeWithoutFetchingChunks(String range) {
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, range).exchange()
        .expectStatus().isEqualTo(416)
        .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */12")
        .expectHeader().contentLength(0).expectBody().isEmpty();
    verify(chunkService, never()).requestBinaryChunk(any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"bytes=0-1,5-6", "items=0-1"})
  void unsupportedRangesFallBackToFullResponse(String range) {
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, range).exchange()
        .expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.CONTENT_RANGE)
        .expectBody(byte[].class).isEqualTo(bytes("abcdefghijkl"));
  }

  @Test
  void ifRangeWithoutMatchingValidatorReturnsFullResponse() {
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, "bytes=5-")
        .header(HttpHeaders.IF_RANGE, "\"old-version\"").exchange().expectStatus().isOk()
        .expectBody(byte[].class).isEqualTo(bytes("abcdefghijkl"));
  }

  @Test
  void headReturnsFullLengthWithoutFetchingChunks() {
    client.head().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, "bytes=5-").exchange()
        .expectStatus().isOk().expectHeader().contentLength(12)
        .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
        .expectBody().isEmpty();
    verify(chunkService, never()).requestBinaryChunk(any(), any(), any(), any(), any());
  }

  @Test
  void emptyBinaryHasNoSatisfiableRange() {
    when(service.requestBinaryManifestWithHumanOnlyCheck(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(new BinaryManifest("video", "video.mp4", "video/mp4", null,
            List.of(), null, null)));
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, "bytes=0-").exchange()
        .expectStatus().isEqualTo(416)
        .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */0").expectBody().isEmpty();
    client.get().uri("/binary/stream/workspace/video/video.mp4").exchange().expectStatus().isOk()
        .expectHeader().contentLength(0).expectBody().isEmpty();
  }

  @Test
  void seeksBeyondFourGigabytesWithoutIntegerOverflow() {
    when(service.requestBinaryManifestWithHumanOnlyCheck(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(new BinaryManifest("video", "video.mp4", "video/mp4", null,
            List.of(new Datagram(Integer.MAX_VALUE, "", "", 0),
                new Datagram(Integer.MAX_VALUE, "", "", 1), new Datagram(3, "", "", 2)),
            null, null)));
    client.get().uri("/binary/stream/workspace/video/video.mp4").header(HttpHeaders.RANGE, "bytes=4294967295-")
        .exchange().expectStatus().isEqualTo(206)
        .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 4294967295-4294967296/4294967297")
        .expectHeader().contentLength(2).expectBody(byte[].class).isEqualTo(bytes("kl"));
    verify(chunkService).requestBinaryChunk(any(), any(), any(), argThat(chunk -> chunk.order() == 2), any());
  }

  @Test
  void cancellationStopsDownloadingAndCleansUpSessionAndWaiters() {
    CompletableFuture<byte[]> pending = new CompletableFuture<>();
    when(chunkService.requestBinaryChunk(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      invocation.<ConcurrentLinkedQueue<String>>getArgument(4).add("pending-chunk");
      return pending;
    });
    var request = MockServerHttpRequest.get("/binary/stream/workspace/video/video.mp4")
        .header(HttpHeaders.RANGE, "bytes=4-").build();
    var subscription = controller.servePublicBinaryAsStreamWithName("workspace", "video", "video.mp4",
        null, null, null, null, null, request, new MockServerHttpResponse()).subscribe();
    subscription.dispose();

    assertTrue(pending.isCancelled());
    ArgumentCaptor<String> session = ArgumentCaptor.forClass(String.class);
    verify(chunkService).requestBinaryChunk(any(), session.capture(), any(), any(), any());
    verify(service).removeDownloadSession("video", session.getValue());
    verify(pool).removeResponseWaiter("pending-chunk");
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.US_ASCII);
  }
}
