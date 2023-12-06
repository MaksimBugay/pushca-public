package bmv.org.pushca.client.model;

import java.util.Collections;
import java.util.List;

public class ClientFilter {

  public final String workSpaceId;
  public final String accountId;
  public final String deviceId;
  public final String applicationId;
  public final boolean findAny;
  public final List<PClient> exclude;

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
}
