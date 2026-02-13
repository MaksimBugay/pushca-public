package bmv.pushca.binary.proxy.api.request;

public record PublishRemoteStreamRequest(String url, boolean forHuman, Long expiredAt) {

  public PublishRemoteStreamRequest(String url) {
    this(url, false, null);
  }
}
