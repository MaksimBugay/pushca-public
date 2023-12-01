package bmv.org.pushcaverifier.client;

import bmv.org.pushcaverifier.client.rest.PusherAddress;

public interface AdvertisedUrlExtractor {

  String getAdvertisedUrl(PusherAddress address);
}
