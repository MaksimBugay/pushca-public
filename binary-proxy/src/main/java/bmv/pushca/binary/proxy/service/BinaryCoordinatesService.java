package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.bytesToInt;

import bmv.pushca.binary.proxy.encryption.EncryptionService;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class BinaryCoordinatesService {

  private static final int COORDINATES_BYTES = 12;
  // Twelve bytes are sixteen unpadded Base64URL characters; legacy encrypted tokens are longer.
  private static final int PLAIN_SUFFIX_LENGTH = 16;

  private final EncryptionService encryptionService;

  public BinaryCoordinatesService(EncryptionService encryptionService) {
    this.encryptionService = encryptionService;
  }

  public BinaryCoordinates retrieve(String suffix) {
    byte[] decBytes;
    try {
      decBytes = suffix.length() == PLAIN_SUFFIX_LENGTH
          ? Base64.getUrlDecoder().decode(suffix)
          : encryptionService.decryptToBytes(suffix);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid binary coordinates", e);
    }
    if (decBytes.length != COORDINATES_BYTES) {
      throw new IllegalArgumentException("Invalid binary coordinates length");
    }
    return new BinaryCoordinates(
        bytesToInt(Arrays.copyOfRange(decBytes, 4, 8)),
        bytesToInt(Arrays.copyOfRange(decBytes, 8, 12))
    );
  }

  public record BinaryCoordinates(int workspaceIdHash, int binaryIdHash) {

  }
}
