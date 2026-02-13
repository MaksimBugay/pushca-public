package bmv.pushca.binary.proxy.pushca;

import bmv.pushca.binary.proxy.pushca.model.RateLimitCheckResult;
import bmv.pushca.binary.proxy.pushca.model.WsGatewayRateLimitCheckData;
import bmv.pushca.binary.proxy.service.RateLimitService;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class PushcaRateLimitService implements RateLimitService {

  private final WebClient webClient;

  private final String checkRateLimitUrl;

  private final boolean pushcaGatewayRateLimitEnabled;

  public PushcaRateLimitService(WebClient webClient,
                                String pushcaClusterUrl,
                                boolean pushcaGatewayRateLimitEnabled) {
    this.webClient = webClient;
    this.checkRateLimitUrl = pushcaClusterUrl + "/gateway/rate-limit-check";
    this.pushcaGatewayRateLimitEnabled = pushcaGatewayRateLimitEnabled;
  }

  @Override
  public Mono<Boolean> isAllowed(Object rateLimitKey) {
    if (!pushcaGatewayRateLimitEnabled) {
      return Mono.just(Boolean.TRUE);
    }
    if (rateLimitKey instanceof WsGatewayRateLimitCheckData rtData) {
      return webClient.post()
          .uri(checkRateLimitUrl)
          .body(BodyInserters.fromValue(rtData))
          .exchangeToMono(
              response -> response.bodyToMono(RateLimitCheckResult.class)
                  .map(RateLimitCheckResult::allowed)
          );
    }
    return Mono.just(Boolean.FALSE);
  }
}
