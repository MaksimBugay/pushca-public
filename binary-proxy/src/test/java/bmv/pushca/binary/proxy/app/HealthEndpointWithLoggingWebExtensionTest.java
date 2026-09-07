package bmv.pushca.binary.proxy.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.health.actuate.endpoint.*;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.DefaultReactiveHealthContributorRegistry;

class HealthEndpointWithLoggingWebExtensionTest {

    @Test
    void includesSynchronousIndicatorsInReactiveHealthResponse() {
        var registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("websocketPool", (HealthIndicator) () -> Health.down().build());
        var group = mock(HealthEndpointGroup.class);
        when(group.isMember("websocketPool")).thenReturn(true);
        when(group.getStatusAggregator()).thenReturn(StatusAggregator.getDefault());
        when(group.getHttpCodeStatusMapper()).thenReturn(HttpCodeStatusMapper.getDefault());
        var extension = new HealthEndpointWithLoggingWebExtension(
                new DefaultReactiveHealthContributorRegistry(), registry,
                HealthEndpointGroups.of(group, Map.of()));

        var response = extension.health(ApiVersion.V3, WebServerNamespace.SERVER,
                SecurityContext.NONE, true).block();

        assertNotNull(response);
        assertEquals(503, response.getStatus());
        assertNotNull(response.getBody());
        assertEquals(Status.DOWN, response.getBody().getStatus());
    }
}
