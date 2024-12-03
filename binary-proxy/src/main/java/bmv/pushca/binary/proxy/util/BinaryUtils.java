package bmv.pushca.binary.proxy.util;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
      "audio/webm",
      "text/plain"
  );

  private BinaryUtils() {
  }

  public static String patchPrivateUrlSuffix(String suffix) {
    String decodedSuffix = URLDecoder.decode(suffix, StandardCharsets.UTF_8);
    String[] parts = decodedSuffix.split("\\|");
    if (parts.length > 2) {
      return URLEncoder.encode(
          String.format("%s|%s|%s",
              parts[0],
              parts[1],
              calculateSha256(parts[2].getBytes(StandardCharsets.UTF_8))
          ),
          StandardCharsets.UTF_8
      );
    } else {
      return suffix;
    }
  }

  public static boolean canPlayTypeInBrowser(String mimeType) {
    return BROWSER_CAN_PLAY_TYPES.contains(mimeType);
  }

  public static boolean isDownloadBinaryRequestExpired(long exp) {
    return Instant.now().toEpochMilli() > exp;
  }
}
