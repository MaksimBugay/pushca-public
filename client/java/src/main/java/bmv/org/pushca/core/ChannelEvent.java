package bmv.org.pushca.core;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import java.util.List;

public class ChannelEvent {

  public enum EventType {CREATE, ADD_MEMBERS, MARK_AS_READ, REMOVE_MEMBERS, REMOVE, ADD_IMPRESSION, REMOVE_IMPRESSION,
    MEMBER_ACTIVE, MEMBER_NOT_ACTIVE}

  public EventType type;

  public PClient actor;

  public String channelId;

  public List<ClientFilter> filters;

  public PImpression impression;
  public long time;
}
