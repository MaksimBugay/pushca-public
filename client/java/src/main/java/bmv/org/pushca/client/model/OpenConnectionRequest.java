package bmv.org.pushca.client.model;

public class OpenConnectionRequest {
  public PClient client;
  public String pusherInstanceId;

  public OpenConnectionRequest() {
  }

  public OpenConnectionRequest(PClient client, String pusherInstanceId) {
    this.client = client;
    this.pusherInstanceId = pusherInstanceId;
  }
}
