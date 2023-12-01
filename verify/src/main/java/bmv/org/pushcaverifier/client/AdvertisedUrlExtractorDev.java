package bmv.org.pushcaverifier.client;

import bmv.org.pushcaverifier.client.rest.PusherAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
public class AdvertisedUrlExtractorDev implements AdvertisedUrlExtractor{

  public String getAdvertisedUrl(PusherAddress address) {
    return address.externalAdvertisedUrl();
  }
}
