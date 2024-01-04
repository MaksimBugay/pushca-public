package bmv.org.pushca.core;

import bmv.org.pushca.client.model.ClientFilter;
import java.util.Set;

public class ChannelWithInfo {

  public PChannel channel;
  public Set<ClientFilter> members;
  public long counter;
  public long time;
  public boolean read;
}
