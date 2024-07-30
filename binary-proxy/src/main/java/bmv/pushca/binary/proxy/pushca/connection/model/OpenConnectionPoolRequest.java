package bmv.pushca.binary.proxy.pushca.connection.model;


import bmv.pushca.binary.proxy.pushca.model.PClient;

public record OpenConnectionPoolRequest(PClient client, String pusherInstanceId, int poolSize) {

  public OpenConnectionPoolRequest(PClient client, int poolSize) {
    this(client, null, poolSize);
  }
}
