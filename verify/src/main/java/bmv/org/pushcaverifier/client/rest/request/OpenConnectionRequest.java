package bmv.org.pushcaverifier.client.rest.request;


import bmv.org.pushcaverifier.client.PClientWithPusherId;

public record OpenConnectionRequest(PClientWithPusherId client, String pusherInstanceId) {

  public OpenConnectionRequest(PClientWithPusherId client) {
    this(client, null);
  }
}
