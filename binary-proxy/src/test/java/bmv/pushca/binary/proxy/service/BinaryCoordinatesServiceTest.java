package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.intToBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bmv.pushca.binary.proxy.encryption.EncryptionECService;
import bmv.pushca.binary.proxy.service.BinaryCoordinatesService.BinaryCoordinates;
import java.io.File;
import java.nio.file.Paths;
import java.util.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BinaryCoordinatesServiceTest {

  private static EncryptionECService encryptionService;
  private static BinaryCoordinatesService coordinatesService;

  @BeforeAll
  static void setUp() throws Exception {
    String keysPath = Paths.get(ClassLoader.getSystemResource("application-test.yaml").toURI())
        .getParent().toString() + File.separator;
    encryptionService = new EncryptionECService(keysPath, "password123");
    coordinatesService = new BinaryCoordinatesService(encryptionService);
  }

  @Test
  void readsPlainShortSuffix() {
    byte[] bytes = coordinates(123, 456);
    String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    assertEquals(16, suffix.length());
    assertEquals(new BinaryCoordinates(123, 456), coordinatesService.retrieve(suffix));
  }

  @Test
  void stillReadsEncryptedShortSuffix() throws Exception {
    String suffix = encryptionService.encryptBytes(coordinates(123, 456));

    assertTrue(suffix.length() > 16);
    assertEquals(new BinaryCoordinates(123, 456), coordinatesService.retrieve(suffix));
  }

  @Test
  void rejectsMalformedPlainSuffix() {
    assertThrows(IllegalArgumentException.class, () -> coordinatesService.retrieve("!!!!!!!!!!!!!!!!"));
  }

  private static byte[] coordinates(int workspaceHash, int binaryHash) {
    return ArrayUtils.addAll(
        ArrayUtils.addAll(intToBytes(7), intToBytes(workspaceHash)),
        intToBytes(binaryHash)
    );
  }
}
