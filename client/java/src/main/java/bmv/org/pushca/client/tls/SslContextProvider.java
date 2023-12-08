package bmv.org.pushca.client.tls;

import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
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
        sslContext.init(kmf.getKeyManagers(), new TrustManager[] {MOCK_TRUST_MANAGER}, null);
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

  private static final TrustManager MOCK_TRUST_MANAGER = new X509ExtendedTrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {

    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {

    }

    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
      return new java.security.cert.X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {

    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
      // empty method
    }
  };

}
