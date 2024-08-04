package bmv.pushca.binary.proxy.pushca.security;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.StringUtils;

public class SslContextProvider {

  private final SSLContext sslContext;

  public static SslContext buildSslContext(String tlsStorePath, char[] tlsStorePassword)
      throws Exception {
    if (StringUtils.isEmpty(tlsStorePath)) {
      return null;
    }
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (FileInputStream keyStoreFile = new FileInputStream(tlsStorePath)) {
      keyStore.load(keyStoreFile, tlsStorePassword);
    }

    // Initialize the key manager factory
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, tlsStorePassword);

    // Initialize the trust manager factory
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);

    // Create the SSL context
    return SslContextBuilder.forServer(keyManagerFactory)
        .trustManager(trustManagerFactory)
        .sslProvider(SslProvider.JDK)  // or SslProvider.OPENSSL if you prefer OpenSSL
        .build();
  }

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
