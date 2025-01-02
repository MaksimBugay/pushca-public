package bmv.pushca.binary.proxy.service;

import static com.ip2proxy.IP2Proxy.IOModes.IP2PROXY_FILE_IO;
import static com.ip2proxy.IP2Proxy.IOModes.IP2PROXY_MEMORY_MAPPED;

import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.config.GeoLookupConfig;
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import com.ip2proxy.IP2Proxy;
import com.ip2proxy.ProxyResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

@Service
public class IpGeoLookupService implements DisposableBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(IpGeoLookupService.class);

  private static final String PROXY_DB_FILE_NAME = "IP2PROXY-LITE-PX11.BIN";

  private static final String IP4_DB_FILE_NAME = "IP-COUNTRY-REGION-CITY-LATITUDE-LONGITUDE.BIN";

  private final IP2Location ip2Location;

  private final IP2Proxy ip2Proxy;

  private boolean serviceIsReady;

  public IpGeoLookupService(GeoLookupConfig geoLookupConfig) {
    this.ip2Location = new IP2Location();
    this.ip2Proxy = new IP2Proxy();
    try {
      ip2Location.Open(geoLookupConfig.getGeoIpDataPath() + IP4_DB_FILE_NAME,
          geoLookupConfig.isKeepDbInMemory());
      int openResult = ip2Proxy.Open(
          geoLookupConfig.getGeoIpDataPath() + PROXY_DB_FILE_NAME,
          geoLookupConfig.isKeepDbInMemory() ? IP2PROXY_MEMORY_MAPPED : IP2PROXY_FILE_IO
      );
      if (openResult != 0) {
        throw new IllegalStateException("IP proxy DB is not available");
      }
      this.serviceIsReady = true;
    } catch (Exception ex) {
      LOGGER.error("Cannot load data for geo ip lookup", ex);
      this.serviceIsReady = false;
    }
  }

  public GeoLookupResponse resolve(String ipAddress) {
    if (!serviceIsReady) {
      return null;
    }
    try {
      IPResult ipLookupResult = ip2Location.IPQuery(ipAddress);
      if (ipLookupResult == null || !"OK".equals(ipLookupResult.getStatus())) {
        return new GeoLookupResponse(ipAddress);
      }

      ProxyResult proxyLookupResult = ip2Proxy.GetAll(ipAddress);
      ProxyInfo proxyInfo = Optional.ofNullable(proxyLookupResult)
          .filter(proxyResult -> proxyResult.Is_Proxy == 1)
          .map(proxyResult -> new ProxyInfo(
                  proxyResult.Proxy_Type,
                  proxyResult.ASN,
                  proxyResult.ISP,
                  proxyResult.Usage_Type,
                  proxyResult.Threat
              )
          ).orElse(null);
      return new GeoLookupResponse(
          ipAddress,
          null,
          (double) ipLookupResult.getLatitude(),
          (double) ipLookupResult.getLongitude(),
          null,
          ipLookupResult.getCountryShort(),
          ipLookupResult.getCountryLong(),
          ipLookupResult.getCity(),
          Optional.ofNullable(proxyInfo).map(pi -> pi.toString().replace("ProxyInfo", ""))
              .orElse(null)
      );
    } catch (Exception ex) {
      LOGGER.warn("Failed geo lookup attempt: {}", ipAddress);
      return new GeoLookupResponse(ipAddress);
    }
  }


  @Override
  public void destroy() {
    try {
      ip2Location.Close();
      ip2Proxy.Close();
    } catch (Exception ex) {
      LOGGER.warn("Error during close IP databases attempt", ex);
    }
  }

  public record ProxyInfo(String type, String ASN, String ISP, String usage_type,
                          String threat) {

  }

}
