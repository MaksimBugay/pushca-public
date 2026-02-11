package bmv.pushca.binary.proxy.pushca.util;

import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;

/**
 * Utility class for generating deterministic thumbnail names and IDs.
 *
 * <p>Mirrors the JavaScript ThumbnailGenerator logic: for a given binary ID the thumbnail
 * file name is {@code thumbnail-{binaryId}.png} and its ID is a UUID v5 derived from
 * that name using a fixed namespace.</p>
 */
public final class ThumbnailUtils {

  /**
   * Fixed namespace UUID used for UUID-v5 thumbnail ID generation.
   * Must stay in sync with the JavaScript constant
   * {@code ThumbnailGenerator.thumbnailNameSpace}.
   */
  public static final UUID THUMBNAIL_NAMESPACE =
      UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

  public static final String THUMBNAIL_WORKSPACE_ID = "thumbnail";

  private static final NameBasedGenerator UUID_V5_GENERATOR =
      Generators.nameBasedGenerator(THUMBNAIL_NAMESPACE);

  private ThumbnailUtils() {
    // utility class – no instances
  }

  /**
   * Builds the canonical thumbnail file name for the given binary ID.
   *
   * @param binaryId the binary ID (typically a UUID string)
   * @return the thumbnail file name, e.g. {@code thumbnail-abc123.png}
   */
  public static String buildThumbnailName(String binaryId) {
    return buildThumbnailName(binaryId, "image/png");
  }

  public static String buildThumbnailName(String binaryId, String mimeType) {
    return "thumbnail-%s.%s".formatted(binaryId, mimeTypeToFileExtension(mimeType));
  }

  private static String mimeTypeToFileExtension(String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      return "png";
    }
    return switch (mimeType.strip().toLowerCase()) {
      case "image/png" -> "png";
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/webp" -> "webp";
      case "image/bmp", "image/x-ms-bmp" -> "bmp";
      case "image/tiff" -> "tiff";
      case "image/svg+xml" -> "svg";
      case "image/x-icon", "image/vnd.microsoft.icon" -> "ico";
      case "image/avif" -> "avif";
      case "image/heic" -> "heic";
      case "image/heif" -> "heif";
      default -> {
        // Fallback: use the subtype portion of "type/subtype"
        int slash = mimeType.indexOf('/');
        yield (slash >= 0 && slash < mimeType.length() - 1)
            ? mimeType.substring(slash + 1).strip().toLowerCase()
            : "png";
      }
    };
  }

  /**
   * Builds a deterministic UUID-v5 thumbnail ID for the given binary ID.
   *
   * <p>The ID is derived by computing {@code UUID.v5(thumbnailName, THUMBNAIL_NAMESPACE)}
   * where {@code thumbnailName} is the result of {@link #buildThumbnailName(String)}.</p>
   *
   * @param binaryId the binary ID (typically a UUID string)
   * @return a deterministic UUID-v5 identifier for the thumbnail
   */
  public static UUID buildThumbnailId(String binaryId) {
    String thumbnailName = buildThumbnailName(binaryId);
    return UUID_V5_GENERATOR.generate(thumbnailName);
  }
}
