package bmv.org.pushca.core;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import java.util.List;

public class ChannelEvent {

  public enum EventType {CREATE, ADD_MEMBERS, MARK_AS_READ, REMOVE_MEMBERS, REMOVE}

  public EventType type;

  public PClient actor;

  public String channelId;

  List<ClientFilter> filters;
}
