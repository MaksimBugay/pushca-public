package bmv.org.pushcaverifier.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtility {

  public static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private DateTimeUtility() {
  }

  public static Long getCurrentTimestampMs() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS).toEpochMilli();
  }

  public static String timeToFormattedString(long time) {
    LocalDateTime dateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
    return dateTime.format(FORMATTER);
  }
}
