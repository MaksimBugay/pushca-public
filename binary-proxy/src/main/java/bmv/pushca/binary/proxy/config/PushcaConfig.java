package bmv.pushca.binary.proxy.config;

import bmv.pushca.binary.proxy.pushca.security.SslContextProvider;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class PushcaConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaConfig.class);

  @Value("#{environment.PUSHCA_CLUSTER_URL}")
  private String pushcaClusterUrlEnv;

  @Value("${binary-proxy.pushca.cluster.url:}")
  private String pushcaClusterUrl;

  @Value("${binary-proxy.pushca.connection-pool.size:10}")
  private int pushcaConnectionPoolSize;

  private final SslContextProvider sslContextProvider;

  public PushcaConfig(Environment env) {
    String tlsStorePath = env.getProperty("PUSHCA_TLS_STORE_PATH");
    char[] tlsStorePassword =
        Optional.ofNullable(env.getProperty("PUSHCA_TLS_STORE_PASSWORD"))
            .map(String::toCharArray).orElse(null);
    try {
      sslContextProvider = new SslContextProvider(
          tlsStorePath,
          tlsStorePassword
      );
    } catch (Exception ex) {
      LOGGER.error("Cannot initialize tls context: {}", tlsStorePath, ex);
      throw new RuntimeException(ex);
    }
  }

  public SSLContext getSslContext() {
    return sslContextProvider.getSslContext();
  }

  public String getPushcaClusterUrl() {
    if (StringUtils.hasText(pushcaClusterUrlEnv)) {
      return pushcaClusterUrlEnv;
    }
    return pushcaClusterUrl;
  }

  public int getPushcaConnectionPoolSize() {
    return pushcaConnectionPoolSize;
  }
}
