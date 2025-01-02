package bmv.pushca.binary.proxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeoLookupConfig {

  @Value("${binary-proxy.geo-lookup.db.path:/ip-data/}")
  private String geoIpDataPath;

  @Value("${binary-proxy.geo-lookup.db.keep-in-memory:true}")
  private boolean keepDbInMemory;

  public String getGeoIpDataPath() {
    return geoIpDataPath;
  }

  public boolean isKeepDbInMemory() {
    return keepDbInMemory;
  }
}
