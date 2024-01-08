package bmv.org.pushca.core;

import bmv.org.pushca.client.utils.BmvObjectUtils;
import java.util.Objects;

public class PChannel {

  public String id;

  public String name;

  public PChannel() {
  }

  public PChannel(String id, String name) {
    this.id = id;
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PChannel)) {
      return false;
    }
    PChannel pChannel = (PChannel) o;
    return Objects.equals(id, pChannel.id);
  }

  @Override
  public int hashCode() {
    return BmvObjectUtils.calculateStringHashCode(id);
  }
}
