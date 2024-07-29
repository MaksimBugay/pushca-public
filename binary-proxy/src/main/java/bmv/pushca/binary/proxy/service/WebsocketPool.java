package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.jms.kafka.config.MicroserviceWithKafkaConfiguration;
import bmv.pushca.binary.proxy.pushca.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.ClientSearchFilter;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.UUID;
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

  public void completeWithBinaryManifest(BinaryManifest manifest) {
    CompletableFuture<Object> future = waitingHall.asMap().get(manifest.id());
    if (future != null) {
      future.complete(manifest);
    }
  }
}
