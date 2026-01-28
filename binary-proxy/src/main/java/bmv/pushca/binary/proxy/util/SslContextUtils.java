package bmv.pushca.binary.proxy.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings("unused")
public final class SslContextUtils {

  /**
   * Server certificate for secure.fileshare.ovh
   */
  private static final String SERVER_CERTIFICATE =
      """
          -----BEGIN CERTIFICATE-----
          MIIGQDCCBSigAwIBAgIQBaEXzik3iTlFHc0bO+CLGDANBgkqhkiG9w0BAQsFADBgMQswCQYDVQQG
          EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3d3cuZGlnaWNlcnQuY29tMR8w
          HQYDVQQDExZSYXBpZFNTTCBUTFMgUlNBIENBIEcxMB4XDTI1MDkwMzAwMDAwMFoXDTI2MTAwNDIz
          NTk1OVowHzEdMBsGA1UEAxMUc2VjdXJlLmZpbGVzaGFyZS5vdmgwggEiMA0GCSqGSIb3DQEBAQUA
          A4IBDwAwggEKAoIBAQDCoy/RHHiSMMjhodm5M4m9T4B+RmMzRKz/OrmtrOb0lmY6R0TefUr5wFGg
          iuG+tBMN1WUmrFRADdTI9Ur330o2iHnoWJ89RwxLqqAkObHgNH2cUW1TD8d2ynOtoa+rHv2AqMVp
          sXzCU4pw41zDULVgzIT2VMC698WdlREJyhhQXo9DYIo2TlslIDJTYyA3fLmcdDRT6FqHjfBDDbQ8
          WX1ZAj7V44ZFDN9w7tox2/gPYvYNFLWttUzmr3vJTuNhAGjdTcqbOGS582mYic1rW6FtEyG2jP/v
          sxG5fv/ytw/uV02DAAnaoukelvfAC8QB17fYDRjuaVBBvd+K3OtTNmD/AgMBAAGjggM1MIIDMTAf
          BgNVHSMEGDAWgBQM22yCSQ9KZwq4FO56xEhSiOtWODAdBgNVHQ4EFgQUYCDgL8/X+OG/ovSMXQ5f
          uEsv8ZYwOQYDVR0RBDIwMIIUc2VjdXJlLmZpbGVzaGFyZS5vdmiCGHd3dy5zZWN1cmUuZmlsZXNo
          YXJlLm92aDA+BgNVHSAENzA1MDMGBmeBDAECATApMCcGCCsGAQUFBwIBFhtodHRwOi8vd3d3LmRp
          Z2ljZXJ0LmNvbS9DUFMwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF
          BQcDAjA/BgNVHR8EODA2MDSgMqAwhi5odHRwOi8vY2RwLnJhcGlkc3NsLmNvbS9SYXBpZFNTTFRM
          U1JTQUNBRzEuY3JsMHYGCCsGAQUFBwEBBGowaDAmBggrBgEFBQcwAYYaaHR0cDovL3N0YXR1cy5y
          YXBpZHNzbC5jb20wPgYIKwYBBQUHMAKGMmh0dHA6Ly9jYWNlcnRzLnJhcGlkc3NsLmNvbS9SYXBp
          ZFNTTFRMU1JTQUNBRzEuY3J0MAwGA1UdEwEB/wQCMAAwggF8BgorBgEEAdZ5AgQCBIIBbASCAWgB
          ZgB1ANdtfRDRp/V3wsfpX9cAv/mCyTNaZeHQswFzF8DIxWl3AAABmRBK6fUAAAQDAEYwRAIgV6vt
          l3PjD+ALhIhaPpVymCXGBhqD3PYGw/wMDul8ZsICIB7cVLNJEQr9mAe2Rzc5iOVSiTRGeZru+mm1
          GCzsbNJhAHYAwjF+V0UZo0XufzjespBB68fCIVoiv3/Vta12mtkOUs0AAAGZEErqIAAABAMARzBF
          AiEAoGOXZrTRv8OpEwJeqj7lX7LOmU1NLOqLTnB8I0/GKB0CIBNLUmEAAGKOkkkFqS7dQnwyR+Nm
          axCfIH6ZfmFQES4uAHUAlE5Dh/rswe+B8xkkJqgYZQHH0184AgE/cmd9VTcuGdgAAAGZEErqNwAA
          BAMARjBEAiAJ2TtS1BxLAoo8RUscMZIqS35z9cA5jH7c9fl9liOmXwIgHY7n59hQxgiKKpI+HbrY
          tXjqqDsS9Np5Cmceufwn3p8wDQYJKoZIhvcNAQELBQADggEBALjPmUVSK6FmGoTSYUxFpoyn+RQd
          PI5U5OFq0uiBKCnKqzTI6oWY7wf9vC6ugLw2aKCVAGSQ4T7HxXvT1Ok2b1/R7eFjUVPOgPcJfWKE
          kCbSAut5dYaIh0dQCnQav1eb56wxRoc4/kSYULHhvfIbfcohxBJVPOk+TjkLnzGwiJz7qTLckOru
          DztfHZEwnZ5RRRXTYkPOPgkNnXFJzCe3mhmXieY3nsmTM2jzLDXRrM97QW5b2nOvbDVxbOOcV27I
          av6VV7IjeGSWbyF/Zt4BW7V234OOwtOuBBJTMJRqKqtBL3QAa/me4mB50Lsr3Ar2/aKJtn5zRl61
          FZmhMtLJ1V4=
          -----END CERTIFICATE-----
          """;

  /**
   * RapidSSL TLS RSA CA G1 (Intermediate CA)
   */
  private static final String INTERMEDIATE_CA_CERTIFICATE =
      """
          -----BEGIN CERTIFICATE-----
          MIIEszCCA5ugAwIBAgIQCyWUIs7ZgSoVoE6ZUooO+jANBgkqhkiG9w0BAQsFADBhMQswCQYDVQQG
          EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3d3cuZGlnaWNlcnQuY29tMSAw
          HgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBHMjAeFw0xNzExMDIxMjI0MzNaFw0yNzExMDIx
          MjI0MzNaMGAxCzAJBgNVBAYTAlVTMRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3
          dy5kaWdpY2VydC5jb20xHzAdBgNVBAMTFlJhcGlkU1NMIFRMUyBSU0EgQ0EgRzEwggEiMA0GCSqG
          SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC/uVklRBI1FuJdUEkFCuDL/I3aJQiaZ6aibRHjap/ap9zy
          1aYNrphe7YcaNwMoPsZvXDR+hNJOo9gbgOYVTPq8gXc84I75YKOHiVA4NrJJQZ6p2sJQyqx60HkE
          IjzIN+1LQLfXTlpuznToOa1hyTD0yyitFyOYwURM+/CI8FNFMpBhw22hpeAQkOOLmsqT5QZJYeik
          7qlvn8gfD+XdDnk3kkuuu0eG+vuyrSGr5uX5LRhFWlv1zFQDch/EKmd163m6z/ycx/qLa9zyvILc
          7cQpb+k7TLra9WE17YPSn9ANjG+ECo9PDW3N9lwhKQCNvw1gGoguyCQu7HE7BnW8eSSFAgMBAAGj
          ggFmMIIBYjAdBgNVHQ4EFgQUDNtsgkkPSmcKuBTuesRIUojrVjgwHwYDVR0jBBgwFoAUTiJUIBiV
          5uNu5g/6+rkS7QYXjzkwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF
          BQcDAjASBgNVHRMBAf8ECDAGAQH/AgEAMDQGCCsGAQUFBwEBBCgwJjAkBggrBgEFBQcwAYYYaHR0
          cDovL29jc3AuZGlnaWNlcnQuY29tMEIGA1UdHwQ7MDkwN6A1oDOGMWh0dHA6Ly9jcmwzLmRpZ2lj
          ZXJ0LmNvbS9EaWdpQ2VydEdsb2JhbFJvb3RHMi5jcmwwYwYDVR0gBFwwWjA3BglghkgBhv1sAQEw
          KjAoBggrBgEFBQcCARYcaHR0cHM6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzALBglghkgBhv1sAQIw
          CAYGZ4EMAQIBMAgGBmeBDAECAjANBgkqhkiG9w0BAQsFAAOCAQEAGUSlOb4K3WtmSlbmE50UYBHX
          M0SKXPqHMzk6XQUpCheF/4qU8aOhajsyRQFDV1ih/uPIg7YHRtFiCTq4G+zb43X1T77nJgSOI9pq
          /TqCwtukZ7u9VLL3JAq3Wdy2moKLvvC8tVmRzkAe0xQCkRKIjbBG80MSyDX/R4uYgj6ZiNT/Zg6G
          I6RofgqgpDdssLc0XIRQEotxIZcKzP3pGJ9FCbMHmMLLyuBd+uCWvVcF2ogYAawufChS/PT61D9r
          qzPRS5I2uqa3tmIT44JhJgWhBnFMb7AGQkvNq9KNS9dd3GWc17H/dXa1enoxzWjE0hBdFjxPhUb0
          W3wi8o34/m8Fxw==
          -----END CERTIFICATE-----
          """;

  /**
   * DigiCert Global Root G2 (Root CA)
   */
  private static final String ROOT_CA_CERTIFICATE =
      """
          -----BEGIN CERTIFICATE-----
          MIIDjjCCAnagAwIBAgIQAzrx5qcRqaC7KGSxHQn65TANBgkqhkiG9w0BAQsFADBhMQswCQYDVQQG
          EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3d3cuZGlnaWNlcnQuY29tMSAw
          HgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBHMjAeFw0xMzA4MDExMjAwMDBaFw0zODAxMTUx
          MjAwMDBaMGExCzAJBgNVBAYTAlVTMRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3
          dy5kaWdpY2VydC5jb20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IEcyMIIBIjANBgkq
          hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN9RlUyfuI2/Ou8jqJ
          kTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx1x7e/dfgy5SDN67sH0NO
          3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQq2EGnI/yuum06ZIya7XzV+hdG82MHauV
          BJVJ8zUtluNJbd134/tJS7SsVQepj5WztCO7TG1F8PapspUwtP1MVYwnSlcUfIKdzXOS0xZKBgyM
          UNGPHgm+F6HmIcr9g+UQvIOlCsRnKPZzFBQ9RnbDhxSJITRNrw9FDKZJobq7nMWxM4MphQIDAQAB
          o0IwQDAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUTiJUIBiV5uNu
          5g/6+rkS7QYXjzkwDQYJKoZIhvcNAQELBQADggEBAGBnKJRvDkhj6zHd6mcY1Yl9PMWLSn/pvtsr
          F9+wX3N3KjITOYFnQoQj8kVnNeyIv/iPsGEMNKSuIEyExtv4NeF22d+mQrvHRAiGfzZ0JFrabA0U
          WTW98kndth/Jsw1HKj2ZL7tcu7XUIOGZX1NGFdtom/DzMNU+MeKNhJ7jitralj41E6Vf8PlwUHBH
          QRFXGU7Aj64GxJUTFy8bJZ918rGOmaFvE7FBcf6IKshPECBV1/MUReXgRPTqh5Uykw7+U0b6LJ3/
          iyK5S9kJRaTepLiaWN0bfVKfjllDiIGknibVb63dDcY3fe0Dkhvld1927jyNxF1WW6LZZm6zNTfl
          MrY=
          -----END CERTIFICATE-----
          """;

  /**
   * Netskope CA certificate (for corporate proxy environments)
   */
  private static final String NETSKOPE_CA_CERTIFICATE =
      """
          -----BEGIN CERTIFICATE-----
          MIIDPTCCAiWgAwIBAgIRANY1NdZnFEcli5yXAH2k5QEwDQYJKoZIhvcNAQELBQAw
          gb4xCzAJBgNVBAYTAlVTMQswCQYDVQQIEwJDQTESMBAGA1UEBxMJU3Vubnl2YWxl
          MRswGQYDVQQKExJUcmltYmxlIE5hdmlnYXRpb24xKTAnBgNVBAsTIGE0Mzk5Y2I3
          M2M0NjNmMzliOGM5MTJlYmJlZTBlY2UxMR8wHQYDVQQDExZjYS50cmltYmxlLmdv
          c2tvcGUuY29tMSUwIwYJKoZIhvcNAQkBFhZjZXJ0YWRtaW5AbmV0c2tvcGUuY29t
          MB4XDTI1MTIyNDA2NTAxMVoXDTI3MDEyMzA2NTAxMVowHzEdMBsGA1UEAwwUc2Vj
          dXJlLmZpbGVzaGFyZS5vdmgwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATtGF2k
          uEpKZ5wwRXIWPt87HThAD8ZIocn3LquJd09znvIqpUojLYpZ3CJMXBxFmDwfoDw4
          B4xT1KprqXPyrZJSo4GeMIGbMAkGA1UdEwQCMAAwHQYDVR0OBBYEFOWrEYVqd0ic
          ndwZHX+IUopZowBNMB8GA1UdIwQYMBaAFADYeT+9fgvUUrlWGKU9kFVBQzfiMB8G
          A1UdEQQYMBaCFHNlY3VyZS5maWxlc2hhcmUub3ZoMA4GA1UdDwEB/wQEAwIFoDAd
          BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDQYJKoZIhvcNAQELBQADggEB
          AHeucUV+k1XbAcv5WoEHufJmWscuFJDrrju8bpi/KbFIfPXkS5FlE5jk8m3FlyfX
          07vV3wsYuwBRi7bntFiIc3oLb+s4pj+hwxK7YyWWehawdL0HDiQ9dUERrC2QHRMl
          /xjJ9BWU+U8stbHq2yvxNlASPO+gqSxBomA+NiJDfr9IEmbBzM0P/qllWwHDuD9O
          gK5XFJzt5dHZlhINmaia73JtZPVdSpYPcnHcWIxf48wGKRVE6vm9VgBPkJyi1+ih
          E9kRSalJ7WjkW3jsW7zHKqLGD391h0aNNZUrI2Tt3EcKgakUYhvmOkbTsjYD3ysE
          844kQzqZlXD/EpL1NFulCCU=
          -----END CERTIFICATE-----
          """;

  private SslContextUtils() {
  }

  /**
   * Creates an SSLContext that trusts the embedded certificate chain for secure.fileshare.ovh.
   * Includes DigiCert root, intermediate CA, server cert, and Netskope CA for corporate environments.
   */
  public static SSLContext createSslContextWithCert() {
    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      
      // Create a KeyStore containing all trusted certificates
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      
      // Load and add all certificates to the keystore
      String[] certificates = {
          SERVER_CERTIFICATE,
          INTERMEDIATE_CA_CERTIFICATE,
          ROOT_CA_CERTIFICATE,
          NETSKOPE_CA_CERTIFICATE
      };
      String[] aliases = {
          "fileshare-server",
          "rapidssl-intermediate",
          "digicert-root",
          "netskope-ca"
      };
      
      for (int i = 0; i < certificates.length; i++) {
        try (InputStream certInputStream = new ByteArrayInputStream(
            certificates[i].getBytes(StandardCharsets.UTF_8))) {
          Certificate cert = cf.generateCertificate(certInputStream);
          keyStore.setCertificateEntry(aliases[i], cert);
        }
      }

      // Create a TrustManager that trusts all the certificates
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);

      // Create SSLContext with the custom trust manager
      SSLContext sslContext;
      try {
        sslContext = SSLContext.getInstance("TLSv1.2");
      } catch (Exception e) {
        sslContext = SSLContext.getInstance("TLS");
      }
      sslContext.init(null, tmf.getTrustManagers(), null);
      return sslContext;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create SSL context with embedded certificates", e);
    }
  }

  /**
   * Creates an SSLContext using Java's default trust manager.
   */
  public static SSLContext createDefaultSslContext() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, null, null);
      return sslContext;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates an SSLContext that trusts ALL certificates (INSECURE - use only for development/testing).
   */
  public static SSLContext createTrustAllSslContext() {
    try {
      TrustManager[] trustAllCerts = createTrustAllManagers();

      // Try TLSv1.2 first (Java 8 compatible), fall back to TLS
      SSLContext sslContext;
      try {
        sslContext = SSLContext.getInstance("TLSv1.2");
      } catch (Exception e) {
        sslContext = SSLContext.getInstance("TLS");
      }
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      return sslContext;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trust-all SSL context", e);
    }
  }

  /**
   * Installs trust-all SSL context as JVM default (INSECURE - use only for development/testing).
   * Call this once at application startup before any SSL connections are made.
   */
  public static void installTrustAllAsDefault() {
    try {
      TrustManager[] trustAllCerts = createTrustAllManagers();

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

      // Set as default for entire JVM
      SSLContext.setDefault(sslContext);
      HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

      // Disable hostname verification
      HostnameVerifier allHostsValid = (hostname, session) -> true;
      HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

    } catch (Exception e) {
      throw new RuntimeException("Failed to install trust-all SSL context", e);
    }
  }

  private static TrustManager[] createTrustAllManagers() {
    return new TrustManager[] {
        new X509TrustManager() {
          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          @Override
          public void checkClientTrusted(X509Certificate[] certs, String authType) {
            // Trust all
          }

          @Override
          public void checkServerTrusted(X509Certificate[] certs, String authType) {
            // Trust all
          }
        }
    };
  }

  /**
   * Creates an SSLSocketFactory that trusts all certificates and disables endpoint identification.
   * This is needed for Java 8 compatibility with some WebSocket libraries.
   */
  public static SSLSocketFactory createTrustAllSocketFactory() {
    try {
      TrustManager[] trustAllCerts = createTrustAllManagers();
      
      SSLContext sslContext;
      try {
        sslContext = SSLContext.getInstance("TLSv1.2");
      } catch (Exception e) {
        sslContext = SSLContext.getInstance("TLS");
      }
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      
      final SSLSocketFactory originalFactory = sslContext.getSocketFactory();
      
      // Return a wrapper that disables endpoint identification
      return new SSLSocketFactory() {
        @Override
        public String[] getDefaultCipherSuites() {
          return originalFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
          return originalFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
          SSLSocket socket = (SSLSocket) originalFactory.createSocket(s, host, port, autoClose);
          disableEndpointIdentification(socket);
          return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
          SSLSocket socket = (SSLSocket) originalFactory.createSocket(host, port);
          disableEndpointIdentification(socket);
          return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
          SSLSocket socket = (SSLSocket) originalFactory.createSocket(host, port, localHost, localPort);
          disableEndpointIdentification(socket);
          return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
          SSLSocket socket = (SSLSocket) originalFactory.createSocket(host, port);
          disableEndpointIdentification(socket);
          return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
          SSLSocket socket = (SSLSocket) originalFactory.createSocket(address, port, localAddress, localPort);
          disableEndpointIdentification(socket);
          return socket;
        }

        private void disableEndpointIdentification(SSLSocket socket) {
          SSLParameters params = socket.getSSLParameters();
          params.setEndpointIdentificationAlgorithm(null);
          socket.setSSLParameters(params);
        }
      };
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trust-all socket factory", e);
    }
  }
}
