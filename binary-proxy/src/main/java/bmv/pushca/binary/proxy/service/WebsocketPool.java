package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.BINARY_MANIFEST;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.GATEWAY_REQUEST;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.PRIVATE_URL_SUFFIX;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.RESPONSE;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.VALIDATE_PASSWORD_HASH;
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
import bmv.pushca.binary.proxy.service.WsGateway.GatewayRequestData;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class WebsocketPool implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketPool.class);

    /**
     * Flag indicating the pool is shutting down. Once set, no new operations are accepted.
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final AtomicBoolean poolIsReady = new AtomicBoolean(false);

    @SuppressWarnings("rawtypes")
    private final Map<String, ResponseWaiter> waitingHall = new ConcurrentMapWithEvictionAndKeyTtl<>(
            100_000,
            Duration.ofMinutes(30).toMillis()
    );

    private final Map<String, List<String>> activeDownloadSessions = new ConcurrentMapWithEvictionAndKeyTtl<>(
            100_000,
            Duration.ofMinutes(30).toMillis()
    );

    private final PushcaConfig pushcaConfig;
    private final PushcaWsClientFactory pushcaWsClientFactory;
    private final MicroserviceConfiguration microserviceConfiguration;

    private volatile Consumer<GatewayRequestData> gatewayRequestHandler;

    private final Scheduler websocketScheduler;
    private final Scheduler delayedExecutor;
    private final ListWithRandomAccess<NettyWsClient> wsPool =
            new ListWithRandomAccess<>(new CopyOnWriteArrayList<>());

    // Tracked periodic task disposables for explicit cleanup during shutdown
    private final Disposable pingTaskDisposable;
    private final Disposable repeaterTaskDisposable;

    public WebsocketPool(MicroserviceConfiguration configuration,
                         PushcaConfig pushcaConfig,
                         PushcaWsClientFactory pushcaWsClientFactory) {
        this.pushcaConfig = pushcaConfig;
        this.microserviceConfiguration = configuration;
        this.pushcaWsClientFactory = pushcaWsClientFactory;
        this.websocketScheduler =
                Schedulers.newBoundedElastic(configuration.websocketExecutorPoolSize, 1_000,
                        "websocketPoolThreads");
        this.delayedExecutor =
                Schedulers.newBoundedElastic(configuration.delayedExecutorPoolSize, 1_000,
                        "wsPoolDelayedThreads");

        createWebsocketPool();

        // Create periodic tasks with exception-safe initialization
        // If any task creation fails, previously created resources are cleaned up
        Disposable pingTask = null;
        Disposable repeaterTask = null;
        try {
            pingTask = delayedExecutor.schedulePeriodically(
                    () -> {
                        try {
                            if (shuttingDown.get()) {
                                return;
                            }
                            if (!poolIsReady.get()) {
                                return;
                            }
                            logHeapMemory();
                            wsPool.forEach(ws -> {
                                try {
                                    boolean sendResult = ws.send(buildCommandMessage(null, PING).commandBody);
                                    if (!sendResult) {
                                        LOGGER.warn("Failed to send ping to ws {}", ws.getIndexInPool());
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn(
                                            "Unexpected error during send ping to ws attempt {}: {}",
                                            ws.getIndexInPool(), e.getMessage()
                                    );
                                }
                            });
                            LOGGER.debug("Waiting hall size {}", waitingHall.size());
                            LOGGER.debug("Websocket pool size: {}", this.wsPool.size());
                        } catch (Exception e) {
                            LOGGER.error("Error in periodic ping/health task", e);
                        }
                    },
                    25, 30, TimeUnit.SECONDS
            );
            repeaterTask = delayedExecutor.schedulePeriodically(
                    () -> {
                        try {
                            if (shuttingDown.get()) {
                                return;
                            }
                            if (!poolIsReady.get()) {
                                return;
                            }
                            runResponseWaiterRepeater();
                        } catch (Exception e) {
                            LOGGER.error("Error in response waiter repeater task", e);
                        }
                    },
                    10, 10, TimeUnit.SECONDS
            );
            // Both tasks created successfully, assign to final fields
            this.pingTaskDisposable = pingTask;
            this.repeaterTaskDisposable = repeaterTask;
        } catch (Exception e) {
            // Clean up any successfully created periodic task to prevent memory leak
            if (pingTask != null && (!pingTask.isDisposed())) {
                pingTask.dispose();
            }
            //noinspection ConstantValue
            if (repeaterTask != null && (!repeaterTask.isDisposed())) {
                repeaterTask.dispose();
            }
            // Also dispose schedulers since constructor is failing
            delayedExecutor.dispose();
            websocketScheduler.dispose();
            throw e;
        }
    }

    public void setGatewayRequestHandler(
            Consumer<GatewayRequestData> gatewayRequestHandler) {
        this.gatewayRequestHandler = gatewayRequestHandler;
    }

    private void runResponseWaiterRepeater() {
        waitingHall.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .forEach(entry -> completeWithTimeout(entry.getKey()));

        // Execute repeat actions asynchronously to avoid blocking the scheduler thread
        waitingHall.values().forEach(waiter -> {
            try {
                delayedExecutor.schedule(waiter::runRepeatAction, 0, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // Scheduler disposed during shutdown - ignore
                LOGGER.debug("Repeat action rejected during shutdown");
            }
        });
    }

    private void wsConnectionWasOpenHandler(NettyWsClient webSocket) {
        wsPool.add(webSocket);
        poolIsReady.set(wsPool.size() >= pushcaConfig.getPushcaConnectionPoolSize());
    }

    private void wsConnectionWasClosedHandler(NettyWsClient webSocket) {
        wsPool.remove(webSocket);
        poolIsReady.set(wsPool.size() >= pushcaConfig.getPushcaConnectionPoolSize());
    }

    private void wsConnectionMessageWasReceivedHandler(String message) {
        //LOGGER.info("New ws message: {}", message);
        if (message.contains(String.format("::%s::", PRIVATE_URL_SUFFIX.name()))) {
            String[] parts = message.split("::");
            if (parts.length < 3) {
                LOGGER.warn("Malformed PRIVATE_URL_SUFFIX message, expected at least 3 parts: {}", message);
                return;
            }
            completeWithResponse(parts[0], parts[2]);
            return;
        }
        if (message.contains(String.format("::%s::", VALIDATE_PASSWORD_HASH.name()))) {
            String[] parts = message.split("::");
            if (parts.length < 3) {
                LOGGER.warn("Malformed VALIDATE_PASSWORD_HASH message, expected at least 3 parts: {}", message);
                return;
            }
            completeWithResponse(parts[0], Boolean.valueOf(parts[2]));
            return;
        }
        String[] parts = message.split(MESSAGE_PARTS_DELIMITER);
        if ((parts.length > 1) && isValidMessageType(parts[1])) {
            MessageType type = MessageType.valueOf(parts[1]);
            //TODO doesn't was under graalvm
            if (GATEWAY_REQUEST == type) {
                if (parts.length < 4) {
                    LOGGER.warn("Malformed GATEWAY_REQUEST message, expected at least 4 parts: {}", message);
                    return;
                }
                try {
                    Optional.ofNullable(gatewayRequestHandler)
                            .ifPresent(handler -> handler.accept(new GatewayRequestData(
                                    parts[0],
                                    parts[3],
                                    parts[2],
                                    (parts.length >= 5) ? parts[4] : null
                            )));
                } catch (Exception ex) {
                    LOGGER.error("Error processing GATEWAY_REQUEST for id = {}", parts[0], ex);
                }
                return;
            }
            if (BINARY_MANIFEST == type) {
                if (parts.length < 3) {
                    LOGGER.warn("Malformed BINARY_MANIFEST message, expected at least 3 parts: {}", message);
                    return;
                }
                try {
                    sendAcknowledge(parts[0]);
                    BinaryManifest manifest = fromJson(parts[2], BinaryManifest.class);
                    completeWithResponse(manifest.id(), manifest);
                } catch (Exception ex) {
                    LOGGER.error("Error processing BINARY_MANIFEST for id = {}", parts[0], ex);
                }
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
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        if (!poolIsReady.get()) {
            throw new IllegalStateException("WebsocketPool is not ready yet");
        }
        try {
            return wsPool.get();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Pushca connection pool is exhausted", e);
        }
    }

    public void registerDownloadSession(String binaryId, String sessionId) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        // Use single atomic compute() to avoid race condition between putIfAbsent and computeIfPresent
        activeDownloadSessions.compute(binaryId, (id, existingList) -> {
            CopyOnWriteArrayList<String> list = (existingList != null) ?
                    (CopyOnWriteArrayList<String>) existingList
                    :
                    new CopyOnWriteArrayList<>();
            list.addIfAbsent(sessionId);
            return list;
        });
    }

    public void removeDownloadSession(String binaryId, String sessionId) {
        // Use compute() instead of computeIfPresent() to prevent race condition:
        // Another thread could call registerDownloadSession between isEmpty() check and entry removal
        activeDownloadSessions.compute(binaryId, (id, uuids) -> {
            if (uuids == null) {
                return null;
            }
            uuids.remove(sessionId);
            return uuids.isEmpty() ? null : uuids;
        });
    }

    public List<String> getActiveDownloadSession(String binaryId) {
        return activeDownloadSessions.getOrDefault(binaryId, List.of());
    }

    public <T> ResponseWaiter<T> registerResponseWaiter(String waiterId,
                                                        ResponseWaiter<T> responseWaiter) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        waitingHall.put(waiterId, responseWaiter);
        return responseWaiter;
    }

    public <T> ResponseWaiter<T> registerResponseWaiter(
            String waiterId,
            long repeatInterval) {
        if (StringUtils.isBlank(waiterId)) {
            throw new IllegalStateException("waiterId is blank");
        }
        return registerResponseWaiter(waiterId, new ResponseWaiter<>(repeatInterval));
    }

    public void activateResponseWaiter(String waiterId) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        waitingHall.computeIfPresent(waiterId, (wId, waiter) -> {
            waiter.activate();
            return waiter;
        });
    }

    public void removeResponseWaiter(String waiterId) {
        waitingHall.remove(waiterId);
    }

    /**
     * Completes a response waiter with the given response object.
     * <p>
     * IMPORTANT: Callbacks are executed OUTSIDE the map operation to prevent
     * ConcurrentModificationException if callbacks re-enter the map.
     */
    @SuppressWarnings({"rawtypes"})
    public <T> void completeWithResponse(String id, T responseObject) {
        // Capture waiter atomically, then execute callbacks outside the map operation
        ResponseWaiter[] waiterHolder = new ResponseWaiter[1];
        boolean[] shouldComplete = new boolean[1];

        waitingHall.computeIfPresent(id, (wId, waiter) -> {
            if (waiter.isDone() || waiter.isNotActivated()) {
                return waiter;
            }
            // Capture the waiter for callback execution outside this lambda
            waiterHolder[0] = waiter;
            shouldComplete[0] = true;
            // Remove from map - we'll complete it outside
            return null;
        });

        // Execute callbacks OUTSIDE the map operation to prevent re-entrancy issues
        if (shouldComplete[0]) {
            ResponseWaiter waiter = waiterHolder[0];
            try {
                if (waiter.isResponseValid(responseObject)) {
                    waiter.complete(responseObject);
                } else {
                    // Validation failed - put it back if not already done
                    if (!waiter.isDone()) {
                        waitingHall.putIfAbsent(id, waiter);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error completing response waiter for id = {}", id, e);
                // Ensure waiter is completed exceptionally to avoid hanging
                if (!waiter.isDone()) {
                    waiter.completeExceptionally(e);
                }
            }
        }
    }

    /**
     * Completes a response waiter exceptionally with a TimeoutException.
     * <p>
     * IMPORTANT: Callbacks are executed OUTSIDE the map operation to prevent
     * ConcurrentModificationException if callbacks re-enter the map.
     */
    @SuppressWarnings("rawtypes")
    public void completeWithTimeout(String id) {
        // Capture waiter atomically, then execute callbacks outside the map operation
        ResponseWaiter[] waiterHolder = new ResponseWaiter[1];

        waitingHall.computeIfPresent(id, (wId, waiter) -> {
            if (!waiter.isDone()) {
                waiterHolder[0] = waiter;
                return null; // Remove from map
            }
            return waiter;
        });

        // Execute callback OUTSIDE the map operation to prevent re-entrancy issues
        if (waiterHolder[0] != null) {
            try {
                waiterHolder[0].completeExceptionally(new TimeoutException());
            } catch (Exception e) {
                LOGGER.error("Error completing timeout for waiter id = {}", id, e);
            }
        }
    }

    public void sendAcknowledge(String id) {
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("messageId", id);
        sendCommand(null, ACKNOWLEDGE, metaData);
    }

    public String sendCommand(String id, Command command, Map<String, Object> metaData) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        CommandWithId cmd = (metaData == null) ? PushcaMessageFactory.buildCommandMessage(id, command) :
                PushcaMessageFactory.buildCommandMessage(id, command, metaData);
        boolean sendSuccess = getConnection().send(cmd.commandBody);
        if (!sendSuccess) {
            throw new IllegalStateException("Failed send pushca commandment attempt: " + command.name());
        }
        return cmd.id;
        //LOGGER.info("Send pushca command: {}", cmd.commandBody);
    }

    public void runWithDelay(Runnable task, long delayMs) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        try {
            delayedExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler was disposed between shuttingDown check and schedule call
            LOGGER.debug("Task rejected during shutdown: {}", e.getMessage());
        }
    }

    public CompletableFuture<Void> runAsynchronously(Runnable task) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            delayedExecutor.schedule(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, 0, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler was disposed between shuttingDown check and schedule call
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void destroy() {
        if (!shuttingDown.compareAndSet(false, true)) {
            LOGGER.debug("WebsocketPool shutdown already in progress");
            return;
        }

        LOGGER.info("Shutting down WebsocketPool...");

        completePendingWaitersOnShutdown();

        // Close the waiting hall to stop TTL cleanup thread and release resources
        if (waitingHall instanceof AutoCloseable) {
            try {
                ((AutoCloseable) waitingHall).close();
                LOGGER.debug("Waiting hall closed successfully");
            } catch (Exception e) {
                LOGGER.warn("Error closing waiting hall", e);
            }
        }

        // Close the activeDownloadSessions map to stop its TTL cleanup thread
        if (activeDownloadSessions instanceof AutoCloseable) {
            try {
                ((AutoCloseable) activeDownloadSessions).close();
                LOGGER.debug("Active download sessions map closed successfully");
            } catch (Exception e) {
                LOGGER.warn("Error closing active download sessions map", e);
            }
        }

        // Close websocket connections first (while schedulers are still active for callbacks)
        closeWebsocketPool();

        // Dispose periodic tasks explicitly before disposing schedulers
        if (pingTaskDisposable != null && !pingTaskDisposable.isDisposed()) {
            pingTaskDisposable.dispose();
            LOGGER.debug("Ping task disposed");
        }
        if (repeaterTaskDisposable != null && !repeaterTaskDisposable.isDisposed()) {
            repeaterTaskDisposable.dispose();
            LOGGER.debug("Repeater task disposed");
        }

        // Dispose schedulers after connections are closed
        delayedExecutor.dispose();
        websocketScheduler.dispose();

        LOGGER.info("WebsocketPool shutdown complete");
    }

    public void checkHealth() {
        if (shuttingDown.get()) {
            throw new IllegalStateException("WebsocketPool is shutting down");
        }
        if (((1.0 * wsPool.size()) / pushcaConfig.getPushcaConnectionPoolSize()) < 0.7) {
            throw new IllegalStateException("Pushca connection pool is broken");
        }
    }

    /**
     * Immediately closes all websocket connections without waiting.
     * For shutdown, use {@link #destroy()} instead.
     * Thread-safe: takes a snapshot before closing to avoid race conditions.
     */
    public void closeWebsocketPool() {
        // Take a snapshot of current connections and clear atomically
        // CopyOnWriteArrayList's iterator is a snapshot, but we need to clear too
        List<NettyWsClient> toClose;
        synchronized (wsPool.list()) {  // CopyOnWriteArrayList supports synchronized block
            toClose = new ArrayList<>(wsPool.list());
            wsPool.list().clear();
        }
        // Now disconnect all captured connections
        for (NettyWsClient nettyWsClient : toClose) {
            nettyWsClient.disconnect().block();
        }
    }

    public void createWebsocketPool() {
        runWithDelay(
                () -> {
                    if (shuttingDown.get()) {
                        return;
                    }
                    if (poolIsReady.get()) {
                        return;
                    }
                    pushcaWsClientFactory.createConnectionPool(
                                    pushcaConfig.getPushcaConnectionPoolSize(), null,
                                    (pusherAddress) -> microserviceConfiguration.dockerized
                                            ? pusherAddress.internalAdvertisedUrl()
                                            : pusherAddress.externalAdvertisedUrl(),
                                    this::wsConnectionMessageWasReceivedHandler,
                                    this::wsConnectionDataWasReceivedHandler,
                                    this::wsConnectionWasOpenHandler,
                                    this::wsConnectionWasClosedHandler,
                                    websocketScheduler)
                            .subscribeOn(websocketScheduler)  // Ensure non-blocking execution on dedicated scheduler
                            .subscribe(
                                    pool -> {
                                        for (int i = 0; i < pool.size(); i++) {
                                            final int index = i;
                                            runWithDelay(() -> pool.get(index).openConnection(), 1500L * i);
                                        }
                                        LOGGER.info("WebSocket pool size: {}", pool.size());
                                    },
                                    error -> LOGGER.error("Failed to create WebSocket pool", error)
                            );
                },
                500
        );
    }

    private void completePendingWaitersOnShutdown() {
        if (waitingHall.isEmpty()) {
            return;
        }
        IllegalStateException shutdownException =
                new IllegalStateException("WebsocketPool is shutting down");
        waitingHall.forEach((id, waiter) -> {
            if (!waiter.isDone()) {
                waiter.completeExceptionally(shutdownException);
            }
        });
        waitingHall.clear();
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
