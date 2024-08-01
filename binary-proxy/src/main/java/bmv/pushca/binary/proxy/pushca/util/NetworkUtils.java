package bmv.pushca.binary.proxy.pushca.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtils.class);

  private NetworkUtils() {
  }

  public static String getInternalIpAddress() {
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          InetAddress inetAddress = inetAddresses.nextElement();
          if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
            return inetAddress.getHostAddress();
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.error("Impossible to retrieve internal IP", ex);
    }
    return null;
  }
}
