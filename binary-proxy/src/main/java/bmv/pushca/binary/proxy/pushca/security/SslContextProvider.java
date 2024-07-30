package bmv.pushca.binary.proxy.pushca.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.StringUtils;

public class SslContextProvider {

  private final SSLContext sslContext;

  public SslContextProvider(String tlsStorePath, char[] tlsStorePassword) {
    SSLContext sslContext = null;
    if (StringUtils.isNotEmpty(tlsStorePath)) {
      try (
          FileInputStream pkcs12InputStream = new FileInputStream(tlsStorePath)) {
        KeyStore pkcs12KeyStore = KeyStore.getInstance("PKCS12");
        pkcs12KeyStore.load(pkcs12InputStream, tlsStorePassword);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(pkcs12KeyStore, tlsStorePassword);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(pkcs12KeyStore);

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        sslContext.getServerSessionContext().setSessionTimeout(30);
        sslContext.getClientSessionContext().setSessionTimeout(30);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    this.sslContext = sslContext;
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

}
