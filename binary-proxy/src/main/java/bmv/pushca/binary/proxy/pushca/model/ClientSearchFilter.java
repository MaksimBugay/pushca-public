package bmv.pushca.binary.proxy.pushca.model;

import java.util.List;

public interface ClientSearchFilter {

  String workSpaceId();

  String accountId();

  String deviceId();

  String applicationId();

  boolean findAny();

  List<PClient> exclude();

  boolean isFullyDefined();

  boolean isEmpty();
}
