package bmv.test.com.util;

import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.serialization.json.JsonUtility;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class HttpsURLConnectionExample {

  public static URI acquireWsConnectionUrl(String pushcaApiUrl, String pusherId, PClient client) {
    try {
      URL url = new URL(pushcaApiUrl + "/open-connection");
      HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
      httpsConn.addRequestProperty("User-Agent", "Mozilla");
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {MOCK_TRUST_MANAGER}, null);
      httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());

      httpsConn.setRequestMethod("POST");
      httpsConn.setRequestProperty("Content-Type", "application/json");
      httpsConn.setRequestProperty("Accept", "application/json");
      httpsConn.setDoOutput(true);
      OpenConnectionRequest request = new OpenConnectionRequest(client, pusherId);
      try (OutputStream os = httpsConn.getOutputStream()) {
        byte[] input = toJson(request).getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }
      StringBuilder responseJson = new StringBuilder();
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(httpsConn.getInputStream(), StandardCharsets.UTF_8))) {
        String responseLine;
        while ((responseLine = br.readLine()) != null) {
          responseJson.append(responseLine.trim());
        }
      }
      OpenConnectionResponse response =
          JsonUtility.fromJson(responseJson.toString(), OpenConnectionResponse.class);
      return new URI(response.externalAdvertisedUrl);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static final TrustManager MOCK_TRUST_MANAGER = new X509ExtendedTrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {

    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {

    }

    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
      return new java.security.cert.X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {

    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
        throws CertificateException {
      // empty method
    }
  };

}
