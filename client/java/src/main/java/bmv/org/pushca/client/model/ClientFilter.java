package bmv.org.pushca.client.model;

import java.util.Collections;
import java.util.List;

public class ClientFilter {

  public String workSpaceId;
  public String accountId;
  public String deviceId;
  public String applicationId;
  public boolean findAny;
  public List<PClient> exclude;

  public ClientFilter() {
  }

  public ClientFilter(String workSpaceId, String accountId, String deviceId, String applicationId,
      boolean findAny, List<PClient> exclude) {
    this.workSpaceId = workSpaceId;
    this.accountId = accountId;
    this.deviceId = deviceId;
    this.applicationId = applicationId;
    this.findAny = findAny;
    this.exclude = exclude;
  }

  public ClientFilter(String workSpaceId, String accountId, String deviceId, String applicationId) {
    this(workSpaceId, accountId, deviceId, applicationId, false, Collections.emptyList());
  }

  public ClientFilter(PClient client) {
    this(client.workSpaceId, client.accountId, client.deviceId, client.applicationId);
  }

  public static ClientFilter fromClientWithoutDeviceId(PClient client) {
    return new ClientFilter(client.workSpaceId, client.accountId, null, client.applicationId);
  }
}
