package bmv.pushca.binary.proxy.api.response;

public record PublishRemoteStreamResponse(String url, String error) {

  public PublishRemoteStreamResponse(String url) {
    this(url, "");
  }
}
