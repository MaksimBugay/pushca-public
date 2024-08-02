package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.BINARY_MANIFEST;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.buildCommandMessage;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.isValidMessageType;
import static bmv.pushca.binary.proxy.pushca.model.Command.ACKNOWLEDGE;
import static bmv.pushca.binary.proxy.pushca.model.Command.PING;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.CommandWithId;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType;
import bmv.pushca.binary.proxy.pushca.connection.ListWithRandomAccess;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClient;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.connection.model.BinaryWithHeader;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Command;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  private final Map<String, List<String>> activeDownloadSessions = new ConcurrentHashMap<>();

  private final PushcaConfig pushcaConfig;
  private final Scheduler delayedExecutor;

  private final ListWithRandomAccess<PushcaWsClient> wsPool =
      new ListWithRandomAccess<>(new CopyOnWriteArrayList<>());

  public WebsocketPool(MicroserviceConfiguration configuration,
      PushcaConfig pushcaConfig,
      PushcaWsClientFactory pushcaWsClientFactory) {
    this.pushcaConfig = pushcaConfig;
    this.waitingHall =
        Caffeine.newBuilder()
            .expireAfterWrite(configuration.responseTimeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .buildAsync((key, ignored) -> null);
    this.delayedExecutor =
        Schedulers.newBoundedElastic(configuration.delayedExecutorPoolSize, 10_000,
            "delayedExecutionThreads");

    runWithDelay(() -> {
      pushcaWsClientFactory.createConnectionPool(
          pushcaConfig.getPushcaConnectionPoolSize(), null,
          (pusherAddress) -> configuration.dockerized ? pusherAddress.internalAdvertisedUrl()
              : pusherAddress.externalAdvertisedUrl(),
          this::wsConnectionMessageWasReceivedHandler,
          this::wsConnectionDataWasReceivedHandler,
          this::wsConnectionWasOpenHandler,
          this::wsConnectionWasClosedHandler).subscribe(pool -> {
        for (int i = 0; i < pool.size(); i++) {
          final int index = i;
          runWithDelay(() -> pool.get(index).connect(), i * 500L);
        }
      });
    }, 1000);

    delayedExecutor.schedulePeriodically(
        () -> wsPool.forEach(ws -> ws.send(buildCommandMessage(null, PING).commandBody)),
        25, 30, TimeUnit.SECONDS
    );

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

  private void wsConnectionMessageWasReceivedHandler(String message) {
    //LOGGER.info("New ws message: {}", message);
    String[] parts = message.split(MESSAGE_PARTS_DELIMITER);
    if ((parts.length > 1) && isValidMessageType(parts[1])) {
      MessageType type = MessageType.valueOf(parts[1]);
      if (BINARY_MANIFEST == type) {
        BinaryManifest manifest = fromJson(parts[2], BinaryManifest.class);
        completeWithResponse(manifest.id(), manifest);
        sendAcknowledge(parts[0]);
      }
    }
  }

  private void wsConnectionDataWasReceivedHandler(PushcaWsClient ws, ByteBuffer data) {
    //LOGGER.info("New portion of data arrived: {}", data.array().length);
    BinaryWithHeader binaryWithHeader = new BinaryWithHeader(data.array());
    LOGGER.info("New chunk arrived on {}: {}, {}, {}",
        ws.getClientId(),
        binaryWithHeader.binaryId(),
        binaryWithHeader.order(),
        binaryWithHeader.getPayload().length);
    for (String downloadSessionId : getActiveDownloadSession(
        binaryWithHeader.binaryId().toString())) {
      completeWithResponse(
          concatParts(binaryWithHeader.getDatagramId(), downloadSessionId),
          buildPreviousChunkId(binaryWithHeader, downloadSessionId),
          binaryWithHeader.getPayload()
      );
    }
    if (binaryWithHeader.withAcknowledge()) {
      sendAcknowledge(binaryWithHeader.getDatagramId());
    }
  }

  private String buildPreviousChunkId(BinaryWithHeader chunk, String downloadSessionId) {
    return chunk.order() == 0 ? null : concatParts(
        Datagram.buildDatagramId(
            chunk.binaryId().toString(),
            chunk.order() - 1,
            chunk.clientHash()
        ),
        downloadSessionId
    );
  }

  public PushcaWsClient getConnection() {
    if (wsPool.isEmpty()) {
      throw new IllegalStateException("Pushca connection pool is exhausted");
    }
    return wsPool.get();
  }

  public void registerDownloadSession(String binaryId, String sessionId) {
    activeDownloadSessions.putIfAbsent(binaryId, new CopyOnWriteArrayList<>());
    activeDownloadSessions.computeIfPresent(
        binaryId,
        (id, uuids) -> {
          uuids.add(sessionId);
          return uuids;
        }
    );
  }

  public void removeDownloadSession(String binaryId, String sessionId) {
    activeDownloadSessions.computeIfPresent(
        binaryId,
        (id, uuids) -> {
          uuids.remove(sessionId);
          return uuids.isEmpty() ? null : uuids;
        }
    );
  }

  public List<String> getActiveDownloadSession(String binaryId) {
    return activeDownloadSessions.getOrDefault(binaryId, List.of());
  }

  public <T> ResponseWaiter<T> registerResponseWaiter(String waiterId,
      ResponseWaiter<T> responseWaiter) {
    waitingHall.put(waiterId, responseWaiter);
    return responseWaiter;
  }

  public <T> ResponseWaiter<T> registerResponseWaiter(String waiterId) {
    return registerResponseWaiter(waiterId, new ResponseWaiter<>());
  }

  public <T> void completeWithResponse(String id, T responseObject) {
    completeWithResponse(id, null, responseObject);
  }

  public <T> void completeWithResponse(String id, String previousId, T responseObject) {
    if (previousId != null) {
      ResponseWaiter<T> waiter = getWaiter(previousId);
      if (waiter != null && !waiter.isDone()) {
        runWithDelay(() -> completeWithResponse(id, previousId, responseObject), 100);
        return;
      }
    }
    ResponseWaiter<T> waiter = getWaiter(id);
    if (waiter != null && !waiter.isDone()) {
      if (waiter.isResponseValid(responseObject)) {
        waiter.complete(responseObject);
        waitingHall.asMap().remove(id);
      }
    }
  }

  public <T> void completeWithTimeout(String id) {
    ResponseWaiter<T> waiter = getWaiter(id);
    if (waiter != null && !waiter.isDone()) {
      waiter.completeExceptionally(new TimeoutException());
      waitingHall.asMap().remove(id);
    }
  }

  private <T> ResponseWaiter<T> getWaiter(String id) {
    return (ResponseWaiter<T>) waitingHall.asMap().get(id);
  }

  public void sendAcknowledge(String id) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("messageId", id);
    sendCommand(null, ACKNOWLEDGE, metaData);
  }

  public void sendCommand(String id, Command command, Map<String, Object> metaData) {
    CommandWithId cmd = (metaData == null) ? PushcaMessageFactory.buildCommandMessage(id, command) :
        PushcaMessageFactory.buildCommandMessage(id, command, metaData);
    getConnection().send(cmd.commandBody);
    //LOGGER.info("Send pushca command: {}", cmd.commandBody);
  }

  public void runWithDelay(Runnable task, long delayMs) {
    delayedExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    wsPool.forEach(WebSocketClient::close);
    Optional.ofNullable(delayedExecutor).ifPresent(Scheduler::dispose);
  }

  public void isHealthy() {
    if (((1.0 * wsPool.size()) / pushcaConfig.getPushcaConnectionPoolSize()) < 0.7) {
      throw new IllegalStateException("Pushca connection pool is broken");
    }
  }
}
