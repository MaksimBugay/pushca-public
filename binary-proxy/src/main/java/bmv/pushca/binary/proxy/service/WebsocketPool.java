package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchFilter;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class WebsocketPool implements DisposableBean {

  private final AsyncLoadingCache<String, Object> waitingHall;

  private final Scheduler delayedExecutor;

  public WebsocketPool(MicroserviceConfiguration configuration) {
    this.waitingHall =
        Caffeine.newBuilder()
            .expireAfterWrite(configuration.responseTimeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .buildAsync((key, ignored) -> null);
    this.delayedExecutor =
        Schedulers.newBoundedElastic(configuration.delayedExecutorPoolSize, 10_000,
            "delayedExecutionThreads");
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

  public void runWithDelay(Runnable task, long delayMs) {
    delayedExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    Optional.ofNullable(delayedExecutor).ifPresent(Scheduler::dispose);
  }
}
