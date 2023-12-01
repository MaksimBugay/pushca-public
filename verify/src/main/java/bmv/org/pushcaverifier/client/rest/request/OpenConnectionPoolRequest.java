package bmv.org.pushcaverifier.client.rest.request;


import bmv.org.pushcaverifier.client.PClientWithPusherId;

public record OpenConnectionPoolRequest(PClientWithPusherId client, String pusherInstanceId, int poolSize) {

  public OpenConnectionPoolRequest(PClientWithPusherId client, int poolSize) {
    this(client, null, poolSize);
  }
}
