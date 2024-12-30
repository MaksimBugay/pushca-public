package bmv.pushca.binary.proxy.api.response;

public record GeoLookupResponse(
    String ip,
    Integer accuracyRadius,
    Double latitude,
    Double longitude,
    String timeZone,
    String countryCode,
    String countryName
) {

}
