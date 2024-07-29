package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.util.serialisation.TaskRunner.runWithDelay;

import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.pushca.ClientSearchFilter;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class WebsocketPool {

  private final AsyncLoadingCache<String, Object> waitingHall;

  public WebsocketPool(MicroserviceWithKafkaConfiguration configuration) {
    this.waitingHall =
        Caffeine.newBuilder()
            .expireAfterWrite(configuration.responseTimeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .buildAsync((key, ignored) -> null);
  }

  public void sendUploadBinaryAppeal(ClientSearchFilter owner, String binaryId, int chunkSize,
      boolean manifestOnly, List<Integer> requestedChunks) {

  }

  public <T> CompletableFuture<T> registerResponseFuture(String waiterId, Class<T> responseType) {
    CompletableFuture<T> future = new CompletableFuture<>();
    waitingHall.put(waiterId, future);
    return future;
  }

  public void completeWithResponse(String id, Object responseObject) {
    completeWithResponse(id, null, responseObject);
  }

  public void completeWithResponse(String id, String previousId, Object responseObject) {
    if (previousId != null) {
      CompletableFuture<Object> future = waitingHall.asMap().get(previousId);
      if (future != null && !future.isDone()) {
        runWithDelay(() -> completeWithResponse(id, previousId, responseObject), 100);
        return;
      }
    }
    CompletableFuture<Object> future = waitingHall.asMap().get(id);
    if (future != null) {
      future.complete(responseObject);
    }
  }
}
