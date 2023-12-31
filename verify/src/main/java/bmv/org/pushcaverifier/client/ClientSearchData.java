package bmv.org.pushcaverifier.client;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import bmv.org.pushcaverifier.util.BmvObjectUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

public record ClientSearchData(String workSpaceId, String accountId, String deviceId,
                               String applicationId, boolean findAny, List<PClient> exclude)
    implements ClientSearchFilter {

  public ClientSearchData(String workSpaceId, String accountId, String deviceId,
      String applicationId) {
    this(workSpaceId, accountId, deviceId, applicationId, false, null);
  }

  @JsonIgnore
  public boolean isFullyDefined() {
    return isNotEmpty(workSpaceId) && isNotEmpty(accountId) && isNotEmpty(deviceId)
        && isNotEmpty(applicationId);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return StringUtils.isEmpty(workSpaceId) && StringUtils.isEmpty(accountId)
        && StringUtils.isEmpty(deviceId)
        && StringUtils.isEmpty(applicationId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClientSearchData that)) {
      return false;
    }
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
        (CollectionUtils.isEmpty(exclude) ? "" : exclude.stream()
            .map(client -> String.valueOf(client.hashCode()))
            .collect(Collectors.joining(",")));
    return BmvObjectUtils.calculateStringHashCode(hashStr);
  }
}
