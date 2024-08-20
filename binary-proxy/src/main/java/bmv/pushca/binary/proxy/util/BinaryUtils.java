package bmv.pushca.binary.proxy.util;

import java.time.Instant;
import java.util.Set;

public final class BinaryUtils {

  private static final Set<String> BROWSER_CAN_PLAY_TYPES = Set.of("image/jpeg", "video/mp4");

  private BinaryUtils() {
  }

  public static boolean canPlayTypeInBrowser(String mimeType) {
    return BROWSER_CAN_PLAY_TYPES.contains(mimeType);
  }

  public static boolean isDownloadBinaryRequestExpired(long exp) {
    return Instant.now().toEpochMilli() > exp;
  }
}
