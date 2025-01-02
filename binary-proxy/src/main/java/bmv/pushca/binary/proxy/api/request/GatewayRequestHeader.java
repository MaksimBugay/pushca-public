package bmv.pushca.binary.proxy.api.request;

import bmv.pushca.binary.proxy.pushca.model.PClient;
import java.util.Set;

public class GatewayRequestHeader {

  public PClient client;
  public Set<String> roles;
  public String ip;
}
