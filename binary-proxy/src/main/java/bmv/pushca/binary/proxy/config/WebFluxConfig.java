package bmv.pushca.binary.proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.netty.http.HttpResources;
import reactor.netty.resources.LoopResources;

@Configuration
@EnableWebFlux
public class WebFluxConfig {

  @Bean
  public HttpResources httpResources(MicroserviceConfiguration microserviceConfiguration) {
    return HttpResources.set(
        LoopResources.create(
            "binary-proxy-http-server",
            microserviceConfiguration.webServerSelectorsPoolSize,
            microserviceConfiguration.webServerWorkersPoolSize,
            true
        )
    );
  }
}
