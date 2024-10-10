package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.bytesToInt;

import bmv.pushca.binary.proxy.encryption.EncryptionService;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class BinaryCoordinatesService {

  private final EncryptionService encryptionService;

  public BinaryCoordinatesService(EncryptionService encryptionService) {
    this.encryptionService = encryptionService;
  }

  public BinaryCoordinates retrieve(String encSuffix) {
    byte[] decBytes;
    try {
      decBytes = encryptionService.decryptToBytes(encSuffix);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new BinaryCoordinates(
        bytesToInt(Arrays.copyOfRange(decBytes, 4, 8)),
        bytesToInt(Arrays.copyOfRange(decBytes, 8, 12))
    );
  }

  public record BinaryCoordinates(int workspaceIdHash, int binaryIdHash) {

  }
}
