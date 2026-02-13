package bmv.pushca.binary.proxy.config;

import bmv.pushca.binary.proxy.pushca.security.SslContextProvider;
import io.netty.handler.ssl.SslContext;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class PushcaConfig implements PushcaClusterSecretAware {

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaConfig.class);

  @Value("#{environment.PUSHCA_CLUSTER_URL}")
  private String pushcaClusterUrlEnv;

  @Value("${binary-proxy.pushca.cluster.url:}")
  private String pushcaClusterUrl;

  @Value("${binary-proxy.pushca.cluster.secret:}")
  private String pushcaClusterSecret;

  @Value("#{environment.PUSHCA_GATEWAY_RATELIMIT_ENABLED}")
  private Boolean pushcaGatewayRateLimitEnabledEnv;

  @Value("${binary-proxy.pushca.gateway.ratelimit.enabled:false}")
  private boolean pushcaGatewayRateLimitEnabled;

  @Value("${binary-proxy.pushca.connection-pool.size:10}")
  private int pushcaConnectionPoolSize;

  @Value("#{environment.PUSHCA_PUBLISH_REMOTE_STREAM_URL}")
  private String publishRemoteStreamServicePathEnv;

  @Value("${binary-proxy.pushca.publish-remote-stream.service.path:http://remote-stream-downloader:8000}")
  private String publishRemoteStreamServicePath;

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

  public SslContext getNettySslContext() {
    return sslContextProvider.getNettySslContext();
  }

  public String getPushcaClusterUrl() {
    if (StringUtils.hasText(pushcaClusterUrlEnv)) {
      return pushcaClusterUrlEnv;
    }
    return pushcaClusterUrl;
  }

  public boolean isPushcaGatewayRateLimitEnabled() {
    if (Boolean.TRUE.equals(pushcaGatewayRateLimitEnabledEnv)) {
      return true;
    }
    return pushcaGatewayRateLimitEnabled;
  }

  public String getPushcaClusterSecret() {
    return pushcaClusterSecret;
  }

  public int getPushcaConnectionPoolSize() {
    return pushcaConnectionPoolSize;
  }

  public String getPublishRemoteStreamServicePath() {
    if (StringUtils.hasText(publishRemoteStreamServicePathEnv)) {
      return publishRemoteStreamServicePathEnv;
    }
    return publishRemoteStreamServicePath;
  }
}
