package bmv.org.pushcaverifier.client.rest.response;

public record OpenConnectionResponse(String pusherInstanceId, String externalAdvertisedUrl,
                                     String internalAdvertisedUrl) {

}
