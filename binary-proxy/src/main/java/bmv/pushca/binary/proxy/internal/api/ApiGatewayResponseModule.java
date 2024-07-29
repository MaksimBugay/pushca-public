package bmv.pushca.binary.proxy.internal.api;

import bmv.pushca.binary.proxy.model.InternalMessage;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ApiGatewayResponseModule {

  CompletableFuture<String> registerResponseFuture(InternalMessage message);

  Object prepareResponse(UUID id, Object payload);
}
