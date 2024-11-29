package bmv.pushca.binary.proxy.pushca.util;

import static bmv.pushca.binary.proxy.pushca.util.OwnerSignatureUtils.calculateSpecialSHA256;
import static bmv.pushca.binary.proxy.pushca.util.OwnerSignatureUtils.convertHashToReadableSignature;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class OwnerSignatureUtilsTest {

  @Test
  void convertHashToReadableSignatureTest() {
    String ownerSignature = convertHashToReadableSignature(
        "3nMnnyg8jikO69WPeRvlljY1Va5KNY5lUe8x+htD5n0="
    );
    System.out.println(ownerSignature);

    for (int i = 0; i < 100_000; i++) {
      convertHashToReadableSignature(UUID.randomUUID().toString());
    }
  }

}
