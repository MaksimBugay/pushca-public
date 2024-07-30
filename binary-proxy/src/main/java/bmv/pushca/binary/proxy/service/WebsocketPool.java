package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.ListWithRandomAccess;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClient;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchFilter;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class WebsocketPool implements DisposableBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketPool.class);

  private final AsyncLoadingCache<String, Object> waitingHall;

  private final Scheduler delayedExecutor;

  private final ListWithRandomAccess<PushcaWsClient> wsPool =
      new ListWithRandomAccess<>(new CopyOnWriteArrayList<>());

  public WebsocketPool(MicroserviceConfiguration configuration,
      PushcaConfig pushcaConfig,
      PushcaWsClientFactory pushcaWsClientFactory) {
    this.waitingHall =
        Caffeine.newBuilder()
            .expireAfterWrite(configuration.responseTimeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .buildAsync((key, ignored) -> null);
    this.delayedExecutor =
        Schedulers.newBoundedElastic(configuration.delayedExecutorPoolSize, 10_000,
            "delayedExecutionThreads");

    runWithDelay(() -> {
      List<PushcaWsClient> pool = pushcaWsClientFactory.createConnectionPool(
          pushcaConfig.getPushcaConnectionPoolSize(), null,
          null, null,
          this::wsConnectionWasOpenHandler,
          this::wsConnectionWasClosedHandler);
      for (int i = 0; i < pool.size(); i++) {
        final int index = i;
        runWithDelay(() -> pool.get(index).connect(), i * 500L);
      }
    }, 200);

    runWithDelay(() -> {
      LOGGER.info("Pushca connection pool: size = {}", wsPool.size());
    }, 10000);
  }

  private void wsConnectionWasOpenHandler(PushcaWsClient webSocket) {
    wsPool.add(webSocket);
  }

  private void wsConnectionWasClosedHandler(PushcaWsClient webSocket, int code) {
    wsPool.remove(webSocket);
  }

  private PushcaWsClient getConnection() {
    if (wsPool.isEmpty()) {
      throw new IllegalStateException("Pushca connection pool is exhausted");
    }
    return wsPool.get();
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
    wsPool.forEach(WebSocketClient::close);
    Optional.ofNullable(delayedExecutor).ifPresent(Scheduler::dispose);
  }
}
