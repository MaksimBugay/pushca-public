package bmv.pushca.binary.proxy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.encryption.EncryptionECService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiControllerTest {

    private static final String WORKSPACE_ID = "workspace-a";
    private static final String BINARY_ID = "binary-a";

    private EncryptionECService encryptionService;
    private ApiController controller;
    private String encryptedSuffix;

    @BeforeEach
    void setUp() throws Exception {
        String keysPath = Paths.get(ClassLoader.getSystemResource("application-test.yaml").toURI())
                .toString()
                .replace("application-test.yaml", "");
        encryptionService = new EncryptionECService(keysPath, "password123");
        controller = new ApiController(null, null, encryptionService, null, null, null, null);
        encryptedSuffix = encryptionService.encrypt(
                new CreatePrivateUrlSuffixRequest(WORKSPACE_ID, BINARY_ID)
        );
    }

    @Test
    void extractsBinaryAndWorkspaceWhenWorkspaceMatches() {
        CreatePrivateUrlSuffixRequest params = controller.decryptPrivateUrlSuffix(
                encryptedSuffix,
                WORKSPACE_ID
        );

        assertEquals(WORKSPACE_ID, params.workspaceId());
        assertEquals(BINARY_ID, params.binaryId());
    }

    @Test
    void rejectsWorkspaceThatDoesNotMatchEncryptedSuffix() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.decryptPrivateUrlSuffix(encryptedSuffix, "tampered-workspace")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Tampered workspace ID", exception.getReason());
    }

    @Test
    void decryptsSuffixEvenWhenWorkspaceIsNotSupplied() {
        CreatePrivateUrlSuffixRequest params = controller.decryptPrivateUrlSuffix(encryptedSuffix, null);

        assertEquals(WORKSPACE_ID, params.workspaceId());
        assertEquals(BINARY_ID, params.binaryId());
    }

    @Test
    void decryptsRequestFromUrlEncodedPipeWrappedSuffix() {
        String fullSuffix = URLEncoder.encode(
                encryptedSuffix + "|encryption-data|owner-signature",
                StandardCharsets.UTF_8
        );

        CreatePrivateUrlSuffixRequest params = controller.decryptPrivateUrlSuffix(
                fullSuffix,
                WORKSPACE_ID
        );

        assertEquals(WORKSPACE_ID, params.workspaceId());
        assertEquals(BINARY_ID, params.binaryId());
    }
}
