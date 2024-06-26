package bmv.org.pushca.client.model;

public class OpenConnectionRequest {

  public PClient client;
  public String pusherInstanceId;
  public String apiKey;
  public String passwordHash;

  public OpenConnectionRequest() {
  }

  public OpenConnectionRequest(PClient client, String pusherInstanceId, String apiKey,
      String passwordHash) {
    this.client = client;
    this.pusherInstanceId = pusherInstanceId;
    this.apiKey = apiKey;
    this.passwordHash = passwordHash;
  }
}
