package bmv.org.pushca.client.model;

import bmv.org.pushca.client.utils.BmvObjectUtils;
import java.text.MessageFormat;
import java.util.Objects;

public class PClient {

  public String workSpaceId;
  public String accountId;
  public String deviceId;
  public String applicationId;

  public PClient() {
  }

  public PClient(String workSpaceId, String accountId, String deviceId, String applicationId) {
    this.workSpaceId = workSpaceId;
    this.accountId = accountId;
    this.deviceId = deviceId;
    this.applicationId = applicationId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PClient)) {
      return false;
    }
    PClient client = (PClient) o;
    return Objects.equals(workSpaceId, client.workSpaceId) && Objects.equals(
        accountId, client.accountId) && Objects.equals(deviceId, client.deviceId)
        && Objects.equals(applicationId, client.applicationId);
  }

  @Override
  public int hashCode() {
    return BmvObjectUtils.calculateStringHashCode(
        MessageFormat.format("{0}@@{1}@@{2}@@{3}", workSpaceId, accountId, deviceId, applicationId)
    );
  }
}
