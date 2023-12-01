package bmv.org.pushcaverifier.client;

public record PClientWithPusherId(String workSpaceId, String accountId, String deviceId, String applicationId,
                                  String pusherInstanceId) {

  public PClientWithPusherId(String workSpaceId, String accountId, String deviceId, String applicationId) {
    this(workSpaceId, accountId, deviceId, applicationId, null);
  }

  public PClientWithPusherId(PClientWithPusherId prototype, String pusherInstanceId) {
    this(prototype.workSpaceId, prototype.accountId, prototype.deviceId, prototype.applicationId,
        pusherInstanceId);
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

}
