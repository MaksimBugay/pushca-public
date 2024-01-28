package bmv.org.pushca.core;

import java.util.List;

public class ResourceImpressionCounters {

  public String resourceId;
  public List<ImpressionCounter> counters;

  public long getCounter(int code) {
    return counters.stream()
        .filter(c -> c.code == code)
        .map(c -> c.counter)
        .findFirst().orElse(0L);
  }
}
