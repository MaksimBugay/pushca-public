package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.encryption.EncryptionECService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class PageIdServiceTest {

    private static PageIdService pageIdService;

    private static final String pushcaClusterSecret = UUID.randomUUID().toString();

    @BeforeAll
    public static void init() throws Exception {
        String path = Paths.get(
                        ClassLoader.getSystemResource("application-test.yaml").toURI())
                .toString();
        String keysPath = path.replace("application-test.yaml", "");

        EncryptionECService encryptionECService = new EncryptionECService(keysPath, "password123");

        pageIdService = new PageIdService(
                encryptionECService,
                () -> pushcaClusterSecret
        );
    }

    @Test
    void generateAndVerifyPageIdTest() throws Exception {
        String apiKey = UUID.randomUUID().toString();
        String clientIp = "127.0.0.1";
        String sessionId = UUID.randomUUID().toString();
        String pageId = pageIdService.generatePageId(
                apiKey,
                clientIp,
                sessionId,
                Map.of()
        );

        System.out.println("SessionId: " + sessionId);
        System.out.println(pageId);
        System.out.println(pageId.getBytes(StandardCharsets.UTF_8).length);

        Map<String, String> pageIdClaims = pageIdService.decryptPageId(pageId);

        Assertions.assertTrue(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        apiKey,
                        clientIp,
                        sessionId,
                        1
                )
        );

        Assertions.assertTrue(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        pushcaClusterSecret,
                        clientIp,
                        sessionId,
                        1
                )
        );

        Assertions.assertFalse(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        apiKey + "!",
                        clientIp,
                        sessionId,
                        1
                )
        );

        Assertions.assertFalse(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        apiKey,
                        clientIp + "1",
                        sessionId,
                        1
                )
        );

        Assertions.assertFalse(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        apiKey,
                        clientIp,
                        sessionId+"_0",
                        1
                )
        );

        Thread.sleep(2000);

        Assertions.assertFalse(
                pageIdService.verifyPageId(
                        pageIdClaims,
                        apiKey,
                        clientIp,
                        sessionId,
                        1
                )
        );
    }
}
