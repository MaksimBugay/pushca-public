package bmv.pushca.binary.proxy.app;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpStatus.OK;

import java.time.Duration;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.boot.actuate.health.ReactiveHealthEndpointWebExtension;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@EndpointWebExtension(endpoint = HealthEndpoint.class)
public class HealthEndpointWithLoggingWebExtension extends ReactiveHealthEndpointWebExtension {

  private static final Logger LOGGER = getLogger("HealthEndpoint");

  @Autowired
  public HealthEndpointWithLoggingWebExtension(
      ReactiveHealthContributorRegistry registry,
      HealthEndpointGroups groups) {
    super(registry, groups, Duration.ofSeconds(3L));
  }

  @Override
  public Mono<WebEndpointResponse<? extends HealthComponent>> health(
      ApiVersion apiVersion,
      WebServerNamespace serverNamespace,
      SecurityContext securityContext,
      boolean showAll, String... path) {
    Mono<WebEndpointResponse<? extends HealthComponent>> mono =
        super.health(apiVersion, serverNamespace, securityContext, showAll, path);
    mono.doOnSuccess(response -> {
      if (response.getStatus() != OK.value()) {
        HealthComponent healthComponent = response.getBody();
        LOGGER.warn("Rate limit service health check result: " + toJson(healthComponent));
      }
    });
    return mono;
  }
}