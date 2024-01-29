package bmv.org.pushca.client;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;

public class TokenHolder {

  private static final String SEPARATOR = "<==>";
  public static final String PATTERN = "{0}" + SEPARATOR + "{1}";
  private final AtomicReference<String> holder = new AtomicReference<>();

  public void set(String token) {
    if (token == null) {
      holder.set(null);
      return;
    }
    holder.set(MessageFormat.format(PATTERN, token, String.valueOf(Instant.now().toEpochMilli())));
  }

  public String get() {
    String value = holder.get();
    if (StringUtils.isNotEmpty(value)) {
      return value.split(SEPARATOR)[0];
    }
    return null;
  }

  public long getTime() {
    String value = holder.get();
    if (StringUtils.isNotEmpty(value)) {
      return Long.parseLong(value.split(SEPARATOR)[1]);
    }
    return 0;
  }
}
