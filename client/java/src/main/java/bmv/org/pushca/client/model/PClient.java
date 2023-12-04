package bmv.org.pushca.client.model;

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
}
