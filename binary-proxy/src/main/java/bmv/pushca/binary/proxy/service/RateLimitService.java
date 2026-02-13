package bmv.pushca.binary.proxy.service;

import reactor.core.publisher.Mono;

public interface RateLimitService {

  Mono<Boolean> isAllowed(Object rateLimitKey);
}
