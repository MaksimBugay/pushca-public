package bmv.org.pushca.core;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import java.util.List;

public class ChannelMessage {

  public PClient sender;
  public String channelId;
  public String messageId;
  public Long sendTime;
  public String body;
  public List<ClientFilter> mentioned;
}
