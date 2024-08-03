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
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
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

  private final Map<String, ResponseWaiter> waitingHall = new ConcurrentHashMap<>();

  private final Map<String, List<String>> activeDownloadSessions = new ConcurrentHashMap<>();

  private final PushcaConfig pushcaConfig;
  private final Scheduler delayedExecutor;

  private final ListWithRandomAccess<PushcaWsClient> wsPool =
      new ListWithRandomAccess<>(new CopyOnWriteArrayList<>());

  public WebsocketPool(MicroserviceConfiguration configuration,
      PushcaConfig pushcaConfig,
      PushcaWsClientFactory pushcaWsClientFactory) {
    this.pushcaConfig = pushcaConfig;
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
        () -> {
          logHeapMemory();
          wsPool.forEach(ws -> ws.send(buildCommandMessage(null, PING).commandBody));
        },
        25, 30, TimeUnit.SECONDS
    );
    delayedExecutor.schedulePeriodically(
        this::runResponseWaiterRepeater,
        10, 10, TimeUnit.SECONDS
    );

    runWithDelay(() -> {
      LOGGER.info("Pushca connection pool: size = {}", wsPool.size());
    }, 10000);
  }

  private void runResponseWaiterRepeater() {
    waitingHall.entrySet().stream()
        .filter(entry -> entry.getValue().isExpired())
        .forEach(entry -> completeWithTimeout(entry.getKey()));

    waitingHall.values().forEach(ResponseWaiter::runRepeatAction);
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
    /*LOGGER.info("New chunk arrived on {}: {}, {}, {}",
        ws.getClientId(),
        binaryWithHeader.binaryId(),
        binaryWithHeader.order(),
        binaryWithHeader.getPayload().length);*/
    for (String downloadSessionId : getActiveDownloadSession(
        binaryWithHeader.binaryId().toString())) {
      completeWithResponse(
          concatParts(binaryWithHeader.getDatagramId(), downloadSessionId),
          binaryWithHeader.getPayload()
      );
    }
    if (binaryWithHeader.withAcknowledge()) {
      sendAcknowledge(binaryWithHeader.getDatagramId());
    }
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

  public <T> ResponseWaiter<T> registerResponseWaiter(String waiterId, long repeatInterval) {
    return registerResponseWaiter(waiterId, new ResponseWaiter<>(repeatInterval));
  }

  public void activateResponseWaiter(String waiterId) {
    waitingHall.computeIfPresent(waiterId, (wId, waiter) -> {
      waiter.activate();
      return waiter;
    });
  }

  public <T> void completeWithResponse(String id, T responseObject) {
    waitingHall.computeIfPresent(id, (wId, waiter) -> {
      if ((!waiter.isDone()) && waiter.isResponseValid(responseObject)) {
        waiter.complete(responseObject);
        return null;
      } else {
        return waiter;
      }
    });
  }

  public void completeWithTimeout(String id) {
    waitingHall.computeIfPresent(id, (wId, waiter) -> {
      if ((!waiter.isDone())) {
        waiter.completeExceptionally(new TimeoutException());
        return null;
      } else {
        return waiter;
      }
    });
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

  public static void logHeapMemory() {
    // Get the Java runtime
    Runtime runtime = Runtime.getRuntime();

    // Calculate the used memory
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    long freeMemory = runtime.freeMemory();
    long totalMemory = runtime.totalMemory();
    long maxMemory = runtime.maxMemory();

    LOGGER.info("Used Memory: {} MB, Free memory: {} MB, Total memory: {} MB, Max memory: {} MB",
        usedMemory / (1024 * 1024),
        freeMemory / (1024 * 1024),
        totalMemory / (1024 * 1024),
        maxMemory / (1024 * 1024)
    );
  }
}
