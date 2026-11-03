package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.util.BinaryUtils.patchPrivateUrlSuffix;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.config.SecurityConfig;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import bmv.pushca.binary.proxy.pushca.exception.DownloadLimitExceededException;
import bmv.pushca.binary.proxy.pushca.exception.BinaryNotFoundException;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.IpGeoLookupService;
import bmv.pushca.binary.proxy.service.PageIdService;
import bmv.pushca.binary.proxy.service.PublishBinaryService;
import bmv.pushca.binary.proxy.service.WebsocketPool;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(ApiController.class)
@Import(SecurityConfig.class)
class ApiControllerIntegrationTest {

    private static final byte[] SHORT_SUFFIX_BYTES = {
            0, 0, 0, 1,
            0, 0, 0, 2,
            0, 0, 0, 3
    };

    @Autowired
    private WebTestClient client;

    @Autowired
    private EncryptionService encryptionService;

    @MockitoBean
    private BinaryProxyService binaryProxyService;

    @MockitoBean
    private WebsocketPool websocketPool;

    @MockitoBean
    private PageIdService pageIdService;

    @MockitoBean
    private IpGeoLookupService ipGeoLookupService;

    @MockitoBean
    private PushcaConfig pushcaConfig;

    @MockitoBean
    private PublishBinaryService publishBinaryService;

    @DynamicPropertySource
    static void encryptionProperties(DynamicPropertyRegistry registry) throws Exception {
        String keysPath = Paths.get(ClassLoader.getSystemResource("application-test.yaml").toURI())
                .getParent()
                .toString() + File.separator;
        registry.add("binary-proxy.encryption.keys.path", () -> keysPath);
        registry.add("binary-proxy.encryption.private-key.pwd", () -> "password123");
    }

    @Test
    void missingFileRedirectsToSharedErrorPage() {
        when(binaryProxyService.requestBinaryManifestWithHumanOnlyCheck(
                eq("workspace-a"), eq("missing"), isNull(), isNull(), any()))
                .thenReturn(Mono.error(new BinaryNotFoundException()));
        client.get().uri("/binary/workspace-a/missing").exchange()
                .expectStatus().isFound()
                .expectHeader().location("/binary/download-limit-exceeded.html?error=binary-not-found")
                .expectBody().isEmpty();
    }

    @Test
    void sharedErrorPageDisplaysNotFoundMessage() {
        client.get().uri("/binary/download-limit-exceeded.html?error=binary-not-found").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(body -> {
                    assertNotNull(body);
                    assertTrue(body.contains("This file could not be found"));
                    assertTrue(body.contains("File not found | Secure FileShare"));
                    org.junit.jupiter.api.Assertions.assertFalse(
                            body.contains("Download limit for this file has been reached"));
                });
    }

    @Test
    void missingManifestRedirectsAndDescriptionReturnsNotFound() {
        when(binaryProxyService.requestBinaryManifestWithHumanOnlyCheck(
                eq("workspace-a"), eq("missing"), isNull(), isNull(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<org.springframework.http.HttpStatus> statusConsumer =
                            invocation.getArgument(4);
                    statusConsumer.accept(org.springframework.http.HttpStatus.NOT_FOUND);
                    return Mono.error(new BinaryNotFoundException());
                });
        when(binaryProxyService.requestBinaryManifest("workspace-a", "missing"))
                .thenReturn(CompletableFuture.failedFuture(new BinaryNotFoundException()));
        client.get().uri("/binary/m/workspace-a/missing").exchange()
                .expectStatus().isFound()
                .expectHeader().location("/binary/download-limit-exceeded.html?error=binary-not-found")
                .expectBody().isEmpty();
        client.post().uri("/binary/m/public").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("workspaceId", "workspace-a", "binaryId", "missing"))
                .exchange().expectStatus().isFound()
                .expectHeader().location("/binary/download-limit-exceeded.html?error=binary-not-found")
                .expectBody().isEmpty();
        client.get().uri("/binary/binary-manifest/workspace-a/missing").exchange()
                .expectStatus().isNotFound().expectBody().isEmpty();
    }

    @Test
    void redirectsToDownloadLimitPageWithoutDownloadHeaders() {
        var datagram = new Datagram(3, "hash", null, 0);
        var manifest = new BinaryManifest(
                "binary-a", "file.zip", "application/zip", null,
                List.of(datagram), null, null, "session-a");
        when(binaryProxyService.requestBinaryManifestWithHumanOnlyCheck(
                eq("workspace-a"), eq("binary-a"),
                isNull(), isNull(),
                any()))
                .thenReturn(Mono.just(manifest));
        when(binaryProxyService.requestBinaryChunk(
                anyString(), anyString(),
                anyString(), any(),
                anyInt(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new DownloadLimitExceededException()));

        client.get().uri("/binary/workspace-a/binary-a").exchange()
                .expectStatus().isFound()
                .expectHeader().location("/binary/download-limit-exceeded.html")
                .expectHeader().doesNotExist("Content-Disposition")
                .expectHeader().doesNotExist("X-Total-Size")
                .expectBody().isEmpty();
        verify(binaryProxyService).removeDownloadSession("binary-a", "session-a");
    }

    @Test
    void servesDownloadLimitErrorPage() {
        client.get().uri("/binary/download-limit-exceeded.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(
                        body -> {
                            assertNotNull(body);
                            assertTrue(
                                    body.contains("Download limit for this file has been reached")
                            );
                        }
                );
    }

    @Test
    void createsPrivateSuffixAndUnfoldsItIntoFullUrlUsingPushcaLookup() throws Exception {
        CreatePrivateUrlSuffixRequest request = new CreatePrivateUrlSuffixRequest(
                "workspace-a",
                "binary-a"
        );
        Map<String, String> suffixesSavedToPushca = new ConcurrentHashMap<>();

        when(binaryProxyService.generatePrivateUrlShortSuffix(request.workspaceId(), request.binaryId()))
                .thenReturn(SHORT_SUFFIX_BYTES);
        when(binaryProxyService.getPrivateUrlSuffix(anyString()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(
                        suffixesSavedToPushca.get(invocation.getArgument(0, String.class))
                ));

        AtomicReference<String> createdSuffix = new AtomicReference<>();
        client.post()
                .uri("/binary/private/create-url-suffix")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(createdSuffix::set);

        assertNotNull(createdSuffix.get());
        String[] suffixParts = createdSuffix.get().split(MESSAGE_PARTS_DELIMITER, 2);
        assertEquals(2, suffixParts.length);
        String binaryCoordinates = suffixParts[0];
        assertEquals(16, binaryCoordinates.length());
        assertArrayEquals(
                SHORT_SUFFIX_BYTES,
                Base64.getUrlDecoder().decode(binaryCoordinates)
        );
        String encryptedRequest = suffixParts[1];
        String fullSuffixSavedToPushca = URLEncoder.encode(
                encryptedRequest + "|encryption-data|owner-signature",
                StandardCharsets.UTF_8
        );
        suffixesSavedToPushca.put(binaryCoordinates, fullSuffixSavedToPushca);
        assertEquals(
                request,
                encryptionService.decrypt(encryptedRequest, CreatePrivateUrlSuffixRequest.class)
        );

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/binary/{binaryCoordinates}")
                        .queryParam("workspace", request.workspaceId())
                        .build(binaryCoordinates))
                .exchange()
                .expectStatus().isFound()
                .expectHeader().location(
                        "/protected-binary.html?suffix="
                                + URLEncoder.encode(
                                patchPrivateUrlSuffix(fullSuffixSavedToPushca),
                                StandardCharsets.UTF_8
                        )
                                + "&workspace=" + request.workspaceId()
                );
        verify(binaryProxyService).getPrivateUrlSuffix(binaryCoordinates);
    }
}
