package bmv.pushca.binary.proxy.app;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpStatus.OK;

import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.ReactiveHealthEndpointWebExtension;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@EndpointWebExtension(endpoint = HealthEndpoint.class)
public class HealthEndpointWithLoggingWebExtension extends ReactiveHealthEndpointWebExtension {

    private static final Logger LOGGER = getLogger("HealthEndpoint");

    @Autowired
    public HealthEndpointWithLoggingWebExtension(
            ReactiveHealthContributorRegistry registry,
            HealthContributorRegistry fallbackRegistry,
            HealthEndpointGroups groups) {
        super(registry, fallbackRegistry, groups, Duration.ofSeconds(3L));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Mono<WebEndpointResponse<? extends HealthDescriptor>> health(
            ApiVersion apiVersion,
            WebServerNamespace serverNamespace,
            SecurityContext securityContext,
            boolean showAll, String... path) {
        Mono<WebEndpointResponse<? extends HealthDescriptor>> mono =
                super.health(apiVersion, serverNamespace, securityContext, showAll, path);
        return mono.doOnSuccess(response -> {
            if (Objects.nonNull(response) && (response.getStatus() != OK.value())) {
                HealthDescriptor healthComponent = response.getBody();
                LOGGER.warn("Binary proxy service health check result: " + toJson(healthComponent));
            }
        });
    }
}
