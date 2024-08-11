package bmv.pushca.binary.proxy.encryption;

import static java.util.concurrent.Executors.newFixedThreadPool;

import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class EncryptionServiceTest {

  private static final SimpleDateFormat SDF =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private static final int N_THREADS = 40;

  private static final String TEST_CLAIM_NAME = "test1";
  private static EncryptionECService encryptionECService;
  private static ExecutorService executorService;

  @BeforeAll
  public static void init() throws Exception {
    String path = Paths.get(
            ClassLoader.getSystemResource("application-test.yaml").toURI())
        .toString();
    String keysPath = path.replace("application-test.yaml", "");

    keysPath = "C:\\mbugai\\work\\mlx\\pushca-public\\binary-proxy\\src\\test\\resources\\";

    encryptionECService =
        new EncryptionECService(keysPath, "password123");
    executorService = newFixedThreadPool(N_THREADS);
  }

  private static void assertDatesEquals(Date expected, Date actual) {
    String expectedStr = SDF.format(expected);
    String actualStr = SDF.format(actual);
    Assertions.assertEquals(expectedStr, actualStr);
  }

  @Test
  void encryptedJwtAndUrlEncodingTest() throws Exception {
    int nMax = 1_000;

    AtomicLong counter = new AtomicLong();
    final long start = System.currentTimeMillis();

    for (int n = 0; n < nMax; n++) {
      CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(
          () -> {
            final JWTClaimsSet jwtClaims = getJwtClaimsSet();
            final String encJwt;
            try {
              String tmpEncJwt =
                  encryptionECService.encrypt(jwtClaims);
              encJwt = URLEncoder
                  .encode(tmpEncJwt, StandardCharsets.UTF_8);
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }

            JWTClaimsSet decryptedJwtClaims;
            try {
              decryptedJwtClaims =
                  encryptionECService.decryptAsClaims(encJwt);
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }

            Assertions.assertEquals(jwtClaims.getJWTID(),
                decryptedJwtClaims.getJWTID());

            counter.incrementAndGet();
            return Boolean.TRUE;
          },
          executorService
      );
      future.whenComplete((result, exception) -> {
        if (exception != null) {
          throw new RuntimeException(exception);
        }
      });
      future.join();
    }

    while (counter.get() < nMax) {
      Thread.sleep(500);
    }

    long executionTime = System.currentTimeMillis() - start;
    System.out.println(counter.get() + " : " + executionTime);
  }

  @Test
  void encryptDecryptJwtECTest() throws Exception {
    JWTClaimsSet jwtClaims = getJwtClaimsSet();

    String encJwt = encryptionECService.encrypt(jwtClaims);
    System.out.println("token size = " + encJwt.length());
    JWTClaimsSet decryptedJwtClaims =
        encryptionECService.decryptAsClaims(encJwt);

    Assertions.assertEquals(jwtClaims.getIssuer(),
        decryptedJwtClaims.getIssuer());
    Assertions.assertEquals(jwtClaims.getSubject(),
        decryptedJwtClaims.getSubject());
    Assertions.assertArrayEquals(
        jwtClaims.getAudience().toArray(new String[] {}),
        decryptedJwtClaims.getAudience().toArray(new String[] {}));
    assertDatesEquals(jwtClaims.getExpirationTime(),
        decryptedJwtClaims.getExpirationTime());
    assertDatesEquals(jwtClaims.getNotBeforeTime(),
        decryptedJwtClaims.getNotBeforeTime());
    assertDatesEquals(jwtClaims.getIssueTime(),
        decryptedJwtClaims.getIssueTime());
    Assertions.assertEquals(jwtClaims.getJWTID(),
        decryptedJwtClaims.getJWTID());
    Assertions.assertEquals(jwtClaims.getClaim(TEST_CLAIM_NAME),
        decryptedJwtClaims.getClaim(TEST_CLAIM_NAME));
  }

  private JWTClaimsSet getJwtClaimsSet() {
    Date now = new Date();
    return new JWTClaimsSet.Builder()
        .issuer("https://openid.net")
        .subject("alice")
        .audience(Arrays.asList("https://app-one.com",
            "https://app-two.com"))
        .expirationTime(new Date(now.getTime()
            + 1000 * 60 * 10)) // expires in 10 minutes
        .claim(TEST_CLAIM_NAME, UUID.randomUUID().toString())
        .notBeforeTime(now)
        .issueTime(now)
        .jwtID(UUID.randomUUID().toString())
        .build();
  }
}
