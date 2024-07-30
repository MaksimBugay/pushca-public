package bmv.pushca.binary.proxy.pushca.connection.model;

public record PusherAddress(String externalAdvertisedUrl,
                            String internalAdvertisedUrl,
                            String browserAdvertisedUrl) {

}
