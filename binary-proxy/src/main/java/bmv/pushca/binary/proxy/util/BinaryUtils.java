package bmv.pushca.binary.proxy.util;

import java.time.Instant;
import java.util.Set;

public final class BinaryUtils {

  private static final Set<String> BROWSER_CAN_PLAY_TYPES = Set.of(
      "image/jpeg",
      "video/mp4",
      "video/webm; codecs=\"vp8, opus\"",
      "video/webm; codecs=\"vp9, opus\"",
      "image/bmp",
      "image/png",
      "audio/webm"
      );

  private BinaryUtils() {
  }

  public static boolean canPlayTypeInBrowser(String mimeType) {
    return BROWSER_CAN_PLAY_TYPES.contains(mimeType);
  }

  public static boolean isDownloadBinaryRequestExpired(long exp) {
    return Instant.now().toEpochMilli() > exp;
  }
}
