package bmv.org.pushca.client.model;

import bmv.org.pushca.client.utils.BmvObjectUtils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClientFilter)) {
      return false;
    }
    ClientFilter that = (ClientFilter) o;
    return findAny == that.findAny && Objects.equals(workSpaceId, that.workSpaceId)
        && Objects.equals(accountId, that.accountId) && Objects.equals(deviceId,
        that.deviceId) && Objects.equals(applicationId, that.applicationId)
        && Objects.deepEquals(
        exclude == null ? null : exclude.toArray(new PClient[] {}),
        that.exclude == null ? null : that.exclude.toArray(new PClient[] {})
    );
  }

  @Override
  public int hashCode() {
    String hashStr = (workSpaceId == null ? "" : workSpaceId) + "|" +
        (accountId == null ? "" : accountId) + "|" +
        (deviceId == null ? "" : deviceId) + "|" +
        (applicationId == null ? "" : applicationId) + "|" +
        findAny + "|" +
        (exclude == null ? "" : exclude.stream()
            .map(client -> String.valueOf(client.hashCode()))
            .collect(Collectors.joining(",")));
    return BmvObjectUtils.calculateStringHashCode(hashStr);
  }


  public static ClientFilter fromClientWithoutDeviceId(PClient client) {
    return new ClientFilter(client.workSpaceId, client.accountId, null, client.applicationId);
  }
}
