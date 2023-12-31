package bmv.org.pushcaverifier.client;

import bmv.org.pushcaverifier.util.BmvObjectUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public record PClient(String workSpaceId, String accountId, String deviceId, String applicationId)
    implements ClientSearchFilter, ClientAware {

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

  public PClient(ClientSearchData clientData) {
    this(clientData.workSpaceId(), clientData.accountId(), clientData.deviceId(),
        clientData.applicationId());
  }

  @JsonIgnore
  @Override
  public boolean findAny() {
    return false;
  }

  @JsonIgnore
  @Override
  public List<PClient> exclude() {
    return null;
  }

  @JsonIgnore
  @Override
  public boolean isFullyDefined() {
    return true;
  }

  @JsonIgnore
  @Override
  public boolean isEmpty() {
    return false;
  }

  @JsonIgnore
  @Override
  public PClient getClient() {
    return this;
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
