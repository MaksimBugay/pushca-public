package bmv.org.pushcaverifier.client;

public record PClient(String workSpaceId, String accountId, String deviceId,
                      String applicationId) {

  public PClient(PClientWithPusherId prototype) {
    this(prototype.workSpaceId(), prototype.accountId(), prototype.deviceId(),
        prototype.applicationId());
  }
}
