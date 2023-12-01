package bmv.org.pushcaverifier.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtility {

  private DateTimeUtility() {
  }

  public static Long getCurrentTimestampMs() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS).toEpochMilli();
  }
}
