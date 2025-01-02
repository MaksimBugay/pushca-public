package bmv.pushca.binary.proxy.api.response;

public record GeoLookupResponse(
    String ip,
    Integer accuracyRadius,
    Double latitude,
    Double longitude,
    String timeZone,
    String countryCode,
    String countryName,
    String city,
    String proxyInfo
) {

  public GeoLookupResponse(String ip) {
    this(ip, null, null, null, null, null, null, null, null);
  }
}
