package bmv.pushca.binary.proxy.pushca.model;

import bmv.pushca.binary.proxy.pushca.BmvObjectUtils;
import java.text.MessageFormat;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public record PClient(String workSpaceId, String accountId, String deviceId, String applicationId)
     {

  public PClient {
    if (StringUtils.isEmpty(workSpaceId)) {
      throw new IllegalArgumentException("Empty workSpaceId for client");
    }
    if (StringUtils.isEmpty(accountId)) {
      throw new IllegalArgumentException("Empty accountId for client");
    }
    if (StringUtils.isEmpty(deviceId)) {
      throw new IllegalArgumentException("Empty deviceId for client");
    }
    if (StringUtils.isEmpty(applicationId)) {
      throw new IllegalArgumentException("Empty applicationId for client");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PClient client)) {
      return false;
    }
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
