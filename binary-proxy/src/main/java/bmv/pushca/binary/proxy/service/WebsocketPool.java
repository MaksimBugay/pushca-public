package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.BINARY_MANIFEST;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.PRIVATE_URL_SUFFIX;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.RESPONSE;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.buildCommandMessage;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.isValidMessageType;
import static bmv.pushca.binary.proxy.pushca.model.Command.ACKNOWLEDGE;
import static bmv.pushca.binary.proxy.pushca.model.Command.PING;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;

import bmv.pushca.binary.proxy.api.response.BooleanResponse;
import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.CommandWithId;
import bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType;
import bmv.pushca.binary.proxy.pushca.connection.ListWithRandomAccess;
import bmv.pushca.binary.proxy.pushca.connection.NettyWsClient;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.connection.model.BinaryWithHeader;
import bmv.pushca.binary.proxy.pushca.connection.model.SimpleWsResponse;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.Command;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
  private final PushcaWsClientFactory pushcaWsClientFactory;
  private final MicroserviceConfiguration microserviceConfiguration;

  private final Scheduler websocketScheduler;
  private final Scheduler delayedExecutor;
  private final ListWithRandomAccess<NettyWsClient> wsNettyPool =
      new ListWithRandomAccess<>(new CopyOnWriteArrayList<>());

  public WebsocketPool(MicroserviceConfiguration configuration,
      PushcaConfig pushcaConfig,
      PushcaWsClientFactory pushcaWsClientFactory) {
    this.pushcaConfig = pushcaConfig;
    this.microserviceConfiguration = configuration;
    this.pushcaWsClientFactory = pushcaWsClientFactory;
    this.websocketScheduler =
        Schedulers.newBoundedElastic(configuration.websocketExecutorPoolSize, 10_000,
            "doRateLimitCheckThreads");
    this.delayedExecutor =
        Schedulers.newBoundedElastic(configuration.delayedExecutorPoolSize, 10_000,
            "delayedExecutionThreads");

    createNettyWebsocketPool();

    delayedExecutor.schedulePeriodically(
        () -> {
          logHeapMemory();
          wsNettyPool.forEach(ws -> ws.send(buildCommandMessage(null, PING).commandBody));
          LOGGER.info("Waiting hall size {}", waitingHall.size());
          LOGGER.info("Websocket pool size: {}", this.wsNettyPool.size());
        },
        25, 30, TimeUnit.SECONDS
    );
    delayedExecutor.schedulePeriodically(
        this::runResponseWaiterRepeater,
        10, 10, TimeUnit.SECONDS
    );
  }

  private void runResponseWaiterRepeater() {
    waitingHall.entrySet().stream()
        .filter(entry -> entry.getValue().isExpired())
        .forEach(entry -> completeWithTimeout(entry.getKey()));

    waitingHall.values().forEach(ResponseWaiter::runRepeatAction);
  }

  private void wsNettyConnectionWasOpenHandler(NettyWsClient webSocket) {
    wsNettyPool.add(webSocket);
  }

  private void wsNettyConnectionWasClosedHandler(NettyWsClient webSocket) {
    wsNettyPool.remove(webSocket);
  }

  private void wsConnectionMessageWasReceivedHandler(String message) {
    //LOGGER.info("New ws message: {}", message);
    if (message.contains(String.format("::%s::", PRIVATE_URL_SUFFIX.name()))) {
      String[] parts = message.split("::");
      completeWithResponse(parts[0], parts[2]);
      return;
    }
    String[] parts = message.split(MESSAGE_PARTS_DELIMITER);
    if ((parts.length > 1) && isValidMessageType(parts[1])) {
      MessageType type = MessageType.valueOf(parts[1]);
      if (BINARY_MANIFEST == type) {
        BinaryManifest manifest = fromJson(parts[2], BinaryManifest.class);
        completeWithResponse(manifest.id(), manifest);
        sendAcknowledge(parts[0]);
        return;
      }
      if (RESPONSE == type) {
        Boolean result = Boolean.FALSE;
        if (parts.length > 2) {
          try {
            SimpleWsResponse wsResponse = fromJson(parts[2], SimpleWsResponse.class);
            byte[] rawResponse = Base64.getDecoder().decode(wsResponse.body());
            String json = new String(rawResponse, StandardCharsets.UTF_8);
            BooleanResponse response = fromJson(json, BooleanResponse.class);
            result = response.result();
          } catch (Exception ex) {
            LOGGER.warn("Invalid response format for id = {}: {}", parts[0], parts[2], ex);
          }
        }
        completeWithResponse(parts[0], result);
      }
    }
  }

  private void wsConnectionDataWasReceivedHandler(NettyWsClient ws, byte[] data) {
    BinaryWithHeader binaryWithHeader = new BinaryWithHeader(data);
    /*LOGGER.info("New chunk arrived on connection {}: {}, {}, {}",
        ws.getIndexInPool(),
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

  public NettyWsClient getConnection() {
    if (wsNettyPool.isEmpty()) {
      throw new IllegalStateException("Pushca connection pool is exhausted");
    }
    return wsNettyPool.get();
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

  public void removeResponseWaiter(String waiterId) {
    waitingHall.remove(waiterId);
  }

  public <T> void completeWithResponse(String id, T responseObject) {
    waitingHall.computeIfPresent(id, (wId, waiter) -> {
      if (waiter.isDone() || waiter.isNotActivated()) {
        return waiter;
      }
      if (waiter.isResponseValid(responseObject)) {
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

  public String sendCommand(String id, Command command, Map<String, Object> metaData) {
    CommandWithId cmd = (metaData == null) ? PushcaMessageFactory.buildCommandMessage(id, command) :
        PushcaMessageFactory.buildCommandMessage(id, command, metaData);
    getConnection().send(cmd.commandBody);
    return cmd.id;
    //LOGGER.info("Send pushca command: {}", cmd.commandBody);
  }

  public void runWithDelay(Runnable task, long delayMs) {
    delayedExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    closeNettyWebsocketPool();
    Optional.ofNullable(delayedExecutor).ifPresent(Scheduler::dispose);
    Optional.ofNullable(websocketScheduler).ifPresent(Scheduler::dispose);
  }

  public void isHealthy() {
    if (((1.0 * wsNettyPool.size()) / pushcaConfig.getPushcaConnectionPoolSize()) < 0.7) {
      throw new IllegalStateException("Pushca connection pool is broken");
    }
  }

  public void closeNettyWebsocketPool() {
    wsNettyPool.forEach(NettyWsClient::disconnect);
  }

  public void createNettyWebsocketPool() {
    runWithDelay(() -> {
      pushcaWsClientFactory.createNettyConnectionPool(
              pushcaConfig.getPushcaConnectionPoolSize(), null,
              (pusherAddress) -> microserviceConfiguration.dockerized
                  ? pusherAddress.internalAdvertisedUrl()
                  : pusherAddress.externalAdvertisedUrl(),
              this::wsConnectionMessageWasReceivedHandler,
              this::wsConnectionDataWasReceivedHandler,
              this::wsNettyConnectionWasOpenHandler,
              this::wsNettyConnectionWasClosedHandler,
              websocketScheduler)
          .subscribe(pool -> {
            for (int i = 0; i < pool.size(); i++) {
              final int index = i;
              runWithDelay(() -> pool.get(index).openConnection(), 1500L * i);
            }
            LOGGER.info("Netty pool size: {}", pool.size());
          });
    }, 5000);
  }

  public static void logHeapMemory() {
    // Get the Java runtime
    Runtime runtime = Runtime.getRuntime();

    // Calculate the used memory
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    long freeMemory = runtime.freeMemory();
    long totalMemory = runtime.totalMemory();
    long maxMemory = runtime.maxMemory();

    LOGGER.info("Free memory: {} MB, Used Memory: {} MB, Total memory: {} MB, Max memory: {} MB",
        freeMemory / (1024 * 1024),
        usedMemory / (1024 * 1024),
        totalMemory / (1024 * 1024),
        maxMemory / (1024 * 1024)
    );
  }
}
