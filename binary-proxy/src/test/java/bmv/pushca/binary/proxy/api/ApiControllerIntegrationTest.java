package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.util.BinaryUtils.patchPrivateUrlSuffix;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.config.SecurityConfig;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
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
                        suffixesSavedToPushca.get(invocation.getArgument(0))
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
