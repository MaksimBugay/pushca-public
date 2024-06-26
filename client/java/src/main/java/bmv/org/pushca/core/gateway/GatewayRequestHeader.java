package bmv.org.pushca.core.gateway;

import bmv.org.pushca.client.model.PClient;
import java.util.Set;

public class GatewayRequestHeader {

  public PClient client;
  public Set<String> roles;
  public String ip;
}
