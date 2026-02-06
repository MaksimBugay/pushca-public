package bmv.pushca.binary.proxy.pushca.connection;

import bmv.pushca.binary.proxy.pushca.SendBinaryAgent;
import bmv.pushca.binary.proxy.pushca.exception.SendBinaryError;
import io.netty.handler.ssl.SslContext;

import java.io.Serial;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.util.retry.Retry;

/**
 * A reactive WebSocket client implementation designed for high-load scenarios.
 * <p>
 * This client provides:
 * <ul>
 *   <li>Thread-safe connection state management</li>
 *   <li>Proper resource lifecycle management (no memory leaks)</li>
 *   <li>Bounded buffers with backpressure support</li>
 *   <li>Comprehensive error handling and logging</li>
 *   <li>Graceful shutdown with proper cleanup</li>
 *   <li>Optional automatic reconnection</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class NettyWsClient implements SendBinaryAgent {

  private static final Logger LOGGER = LoggerFactory.getLogger(NettyWsClient.class);

  public static final String CLUSTER_SECRET_HEADER_NAME = "X-Cluster-Secret";
  private static final String X_REAL_IP_HEADER = "X-Real-IP";

  // Configuration constants
  private static final int DEFAULT_MAX_FRAME_PAYLOAD_LENGTH = 5 * 1024 * 1024; // 5MB
  public static final int DEFAULT_SEND_BUFFER_SIZE = 2 * 1024;
  private static final int DEFAULT_RECEIVE_BUFFER_SIZE = 1024;
  private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(10);
  private static final int DEFAULT_RECEIVE_PREFETCH = 32;
  private static final int DEFAULT_SEND_RETRY_MAX = 10;
  private static final Duration DEFAULT_SEND_RETRY_BACKOFF = Duration.ofMillis(50);
  private static final long LOG_THROTTLE_INTERVAL_MS = 5000; // Log at most once per 5 seconds

  // Immutable configuration
  private final int indexInPool;
  private final String clientIP;
  private final String pushcaClusterSecret;
  private final URI uri;
  private final Scheduler scheduler;
  private final Consumer<String> messageConsumer;
  private final BiConsumer<NettyWsClient, byte[]> dataConsumer;
  private final Consumer<NettyWsClient> afterOpenListener;
  private final Consumer<NettyWsClient> afterCloseListener;
  private final WebSocketClient webSocketClient;

  // Thread-safe state management
  private final AtomicBoolean connectionActive = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean connecting = new AtomicBoolean(false);
  private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
  private final AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
  private final Set<Disposable> activeSendSubscriptions = ConcurrentHashMap.newKeySet();

  // Log throttling to prevent spam under load
  private final AtomicLong lastReceiveSinkWarnTime = new AtomicLong(0);
  private final AtomicLong lastSendRetryWarnTime = new AtomicLong(0);

  // Bounded sinks for backpressure control
  private final Sinks.Many<String> sendSink;
  private final Sinks.Many<byte[]> binarySendSink;
  private final Sinks.Many<byte[]> receiveSink;

  /**
   * Creates a new ReactiveWsClient instance.
   *
   * @param indexInPool         The index of this client in the connection pool
   * @param clientIP            The client IP address for X-Real-IP header
   * @param pushcaClusterSecret The cluster secret for authentication
   * @param uri                 The WebSocket server URI
   * @param messageConsumer     Consumer for text messages (nullable)
   * @param dataConsumer        Consumer for binary data (nullable)
   * @param afterOpenListener   Callback invoked when connection opens (nullable)
   * @param afterCloseListener  Callback invoked when connection closes (nullable)
   * @param sslContext          SSL context for secure connections (nullable for non-SSL)
   * @param scheduler           Scheduler for async operations
   */
  public NettyWsClient(
      int indexInPool,
      String clientIP,
      String pushcaClusterSecret,
      URI uri,
      Consumer<String> messageConsumer,
      BiConsumer<NettyWsClient, byte[]> dataConsumer,
      Consumer<NettyWsClient> afterOpenListener,
      Consumer<NettyWsClient> afterCloseListener,
      SslContext sslContext,
      Scheduler scheduler) {

    this.indexInPool = indexInPool;
    this.clientIP = Objects.requireNonNull(clientIP, "clientIP must not be null");
    this.pushcaClusterSecret = Objects.requireNonNull(pushcaClusterSecret,
        "pushcaClusterSecret must not be null");
    this.uri = Objects.requireNonNull(uri, "uri must not be null");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.afterOpenListener = afterOpenListener;
    this.afterCloseListener = afterCloseListener;

    this.webSocketClient = createWebSocketClient(sslContext);
    this.sendSink = Sinks.many().multicast().onBackpressureBuffer(DEFAULT_SEND_BUFFER_SIZE, false);
    this.binarySendSink = Sinks.many().multicast().onBackpressureBuffer(DEFAULT_SEND_BUFFER_SIZE, false);
    this.receiveSink = Sinks.many().multicast().onBackpressureBuffer(DEFAULT_RECEIVE_BUFFER_SIZE, false);
  }

  // ==================== Public API ====================

  /**
   * Returns the index of this client in the connection pool.
   */
  public int getIndexInPool() {
    return indexInPool;
  }

  /**
   * Returns true if the connection is currently active.
   */
  public boolean isConnected() {
    return connectionActive.get() && !closed.get();
  }

  /**
   * Opens the WebSocket connection.
   * This method is idempotent - calling it multiple times has no additional effect.
   */
  public void openConnection() {
    executeConnection(UnaryOperator.identity());
  }

  /**
   * Opens the WebSocket connection with automatic retry on failure.
   *
   * @param maxRetries Maximum number of retry attempts
   * @param backoff    Initial backoff duration between retries
   */
  public void openConnectionWithRetry(int maxRetries, Duration backoff) {
    executeConnection(mono -> mono.retryWhen(
        Retry.backoff(maxRetries, backoff)
            .filter(throwable -> !closed.get())
            .doBeforeRetry(signal ->
                LOGGER.warn("Retrying WebSocket connection (attempt {}): ws index {}, error: {}",
                    signal.totalRetries() + 1, indexInPool, signal.failure().getMessage()))));
  }

  /**
   * Gracefully disconnects the WebSocket connection.
   * Completes all sinks and releases resources.
   *
   * @return Mono that completes when disconnect is finished
   */
  public Mono<Void> disconnect() {
    return Mono.fromRunnable(() -> performDisconnect(true))
        .subscribeOn(scheduler)
        .then();
  }

  /**
   * Immediately disconnects without waiting for graceful shutdown.
   */
  public void disconnectNow() {
    performDisconnect(false);
  }

  /**
   * Sends a text message through the WebSocket connection.
   *
   * @param message The message to send
   * @return true if the message was queued successfully, false otherwise
   */
  public boolean send(String message) {
    return withActiveConnection(() -> {
      EmitResult result = sendSink.tryEmitNext(message);
      if (result.isFailure()) {
        LOGGER.warn("Failed to queue message for sending: ws index {}, result: {}",
            indexInPool, result);
        return false;
      }
      return true;
    }, false);
  }

  /**
   * Sends binary data through the WebSocket connection.
   *
   * @param bytes The binary data to send
   * @return true if the send operation was initiated successfully, false otherwise
   */
  public boolean sendBytes(byte[] bytes) {
    return withActiveConnection(() -> {
      EmitResult result = binarySendSink.tryEmitNext(bytes);
      if (result.isFailure()) {
        LOGGER.warn("Failed to queue binary data for sending: ws index {}, result: {}",
            indexInPool, result);
        return false;
      }
      return true;
    }, false);
  }

  @Override
  public void send(byte[] bytes) throws SendBinaryError {
    boolean success;
    try {
      success = sendBytes(bytes);
    } catch (Exception ex) {
      throw new SendBinaryError("Unexpected error during send binary data attempt: ws index " + indexInPool, ex);
    }
    if (!success) {
      throw new SendBinaryError("Failed to send binary data: ws index " + indexInPool);
    }
  }

  @Override
  public Mono<Void> sendAsync(byte[] bytes) {
    return Mono.defer(
        () -> {
          if (closed.get() || !connectionActive.get()) {
            return Mono.error(new SendBinaryError("Connection not active: ws index " + indexInPool));
          }
          WebSocketSession session = sessionRef.get();
          if (session == null || !session.isOpen()) {
            return Mono.error(new SendBinaryError("Session not available: ws index " + indexInPool));
          }
          LOGGER.debug("Sending binary message directly: {} bytes, ws index {}", bytes.length, indexInPool);
          return session.send(
                  Mono.just(
                      session.binaryMessage(
                          factory -> factory.wrap(bytes)
                      )
                  )
              )
              .doOnSuccess(
                  signal -> LOGGER.debug("Bytes sent: ws index {}, length {}", indexInPool, bytes.length)
              )
              .doOnError(error -> LOGGER.error("Error sending binary message: ws index {}", indexInPool, error));
        }
    );
  }

  /**
   * Sends binary data through the WebSocket connection using a ByteBuffer.
   *
   * @param byteBuffer The binary data to send
   * @return true if the send operation was initiated successfully, false otherwise
   */
  public boolean send(ByteBuffer byteBuffer) {
    byte[] bytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(bytes);
    return sendBytes(bytes);
  }

  /**
   * Sends a text message with retry on buffer full.
   *
   * @param message The message to send
   */
  public void sendWithRetry(String message) {
    withActiveConnection(() -> {
      AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
      Disposable subscription = emitWithRetry(message)
          .subscribeOn(scheduler)
          .doFinally(signal -> cleanupSendSubscription(subscriptionRef.get()))
          .subscribe();
      subscriptionRef.set(subscription);
      trackSendSubscription(subscription);
      return null;
    }, null);
  }

  private void trackSendSubscription(Disposable subscription) {
    if (subscription != null && !subscription.isDisposed()) {
      activeSendSubscriptions.add(subscription);
    }
  }

  private void cleanupSendSubscription(Disposable subscription) {
    if (subscription != null) {
      activeSendSubscriptions.remove(subscription);
    }
  }

  private void cancelActiveSendSubscriptions() {
    activeSendSubscriptions.forEach(subscription -> {
      if (!subscription.isDisposed()) {
        subscription.dispose();
      }
    });
    activeSendSubscriptions.clear();
  }

  /**
   * Returns a Flux of received binary data as byte arrays.
   * Multiple subscribers are supported.
   */
  public Flux<byte[]> receive() {
    return receiveSink.asFlux();
  }

  /**
   * Returns the current WebSocket session if available.
   */
  public Optional<WebSocketSession> session() {
    return Optional.ofNullable(sessionRef.get())
        .filter(WebSocketSession::isOpen);
  }

  // ==================== Connection Management ====================

  private void executeConnection(UnaryOperator<Mono<Void>> monoTransformer) {
    if (closed.get() || connectionActive.get()) {
      LOGGER.debug("Connection already active or client closed: ws index {}", indexInPool);
      return;
    }
    if (!connecting.compareAndSet(false, true)) {
      LOGGER.debug("Connection already in progress: ws index {}", indexInPool);
      return;
    }
    if (closed.get() || connectionActive.get()) {
      connecting.set(false);
      LOGGER.debug("Connection already active or client closed: ws index {}", indexInPool);
      return;
    }

    Mono<Void> connectionMono = webSocketClient
        .execute(uri, buildHeaders(), this::handleSession)
        .doOnSubscribe(s -> LOGGER.debug("Initiating WebSocket connection: ws index {}", indexInPool));

    Disposable subscription = monoTransformer.apply(connectionMono)
        .doOnError(this::handleConnectionError)
        .doFinally(signal -> handleConnectionTermination(signal.toString()))
        .subscribeOn(scheduler)
        .subscribe();

    disposeAndSet(subscriptionRef, subscription);
  }

  private HttpHeaders buildHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(X_REAL_IP_HEADER, clientIP);
    headers.add(CLUSTER_SECRET_HEADER_NAME, pushcaClusterSecret);
    return headers;
  }

  private void performDisconnect(boolean graceful) {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    LOGGER.debug("{} disconnect: ws index {}", graceful ? "Graceful" : "Immediate", indexInPool);

    if (graceful) {
      closeSessionGracefully();
    }

    cancelActiveSendSubscriptions();
    disposeAndSet(subscriptionRef, null);
    completeSinks();
    invokeListener(afterCloseListener, "afterCloseListener");
    LOGGER.info("WebSocket session closed: ws index {}", indexInPool);
  }

  private void closeSessionGracefully() {
    WebSocketSession session = sessionRef.get();
    if (session != null && session.isOpen()) {
      session.close(CloseStatus.NORMAL)
          .timeout(DEFAULT_CLOSE_TIMEOUT)
          .onErrorResume(e -> {
            LOGGER.warn("Error during session close: ws index {}", indexInPool, e);
            return Mono.empty();
          })
          .subscribeOn(Schedulers.boundedElastic())
          .subscribe();
    }
  }

  private void handleConnectionTermination(String signal) {
    LOGGER.debug("Connection terminated with signal {}: ws index {}", signal, indexInPool);

    connectionActive.set(false);
    connecting.set(false);
    sessionRef.set(null);

    if (closed.compareAndSet(false, true)) {
      completeSinks();
      invokeListener(afterCloseListener, "afterCloseListener");
      LOGGER.info("WebSocket session closed: ws index {}", indexInPool);
    }
  }

  private void handleConnectionError(Throwable error) {
    if (!closed.get()) {
      LOGGER.error("WebSocket connection error: ws index {}", indexInPool, error);
    }
  }

  // ==================== Session Handling ====================

  private Mono<Void> handleSession(WebSocketSession session) {
    sessionRef.set(session);
    connectionActive.set(true);
    connecting.set(false);
    notifySessionOpened();

    Mono<Void> input = createInputPipeline(session);
    Mono<Void> output = createOutputPipeline(session);

    return Mono.when(input, output)
        .doOnTerminate(() -> connectionActive.set(false));
  }

  private Mono<Void> createInputPipeline(WebSocketSession session) {
    return session.receive()
        .limitRate(DEFAULT_RECEIVE_PREFETCH)
        .map(this::extractMessageData)  // Extract data on event loop BEFORE thread switch
        .publishOn(Schedulers.boundedElastic(), DEFAULT_RECEIVE_PREFETCH)  // Then switch threads for processing
        .doOnNext(this::processExtractedMessage)
        .doOnError(error -> LOGGER.error("Error in receive stream: ws index {}", indexInPool, error))
        .onErrorResume(error -> Mono.empty())
        .then();
  }

  /**
   * Extracts message data on the event loop thread before switching schedulers.
   * This copies the data so it can be safely processed after Netty releases the buffer.
   * Note: Do NOT manually release the buffer - Spring handles this automatically.
   */
  private MessageData extractMessageData(WebSocketMessage wsMessage) {
    Type type = wsMessage.getType();
    return switch (type) {
      case TEXT -> new MessageData(type, wsMessage.getPayloadAsText(), null);
      case BINARY -> new MessageData(type, null, extractBytes(wsMessage.getPayload()));
      default -> new MessageData(type, null, null);
    };
    // Spring's WebSocket infrastructure automatically releases the buffer after this
  }

  private void processExtractedMessage(MessageData data) {
    try {
      switch (data.type) {
        case TEXT -> processTextData(data.text);
        case BINARY -> processBinaryData(data.bytes);
        case PING, PONG -> LOGGER.trace("Received {} frame: ws index {}", data.type, indexInPool);
        default -> LOGGER.debug("Received unknown message type: {}, ws index {}", data.type, indexInPool);
      }
    } catch (Exception e) {
      LOGGER.error("Error processing {} message: ws index {}", data.type, indexInPool, e);
    }
  }

  private record MessageData(Type type, String text, byte[] bytes) {
  }

  private Mono<Void> createOutputPipeline(WebSocketSession session) {
    Flux<WebSocketMessage> textMessages = sendSink.asFlux()
        .doOnNext(msg -> LOGGER.debug("Sending text message: {} chars, ws index {}", msg.length(), indexInPool))
        .map(session::textMessage);

    Flux<WebSocketMessage> binaryMessages = binarySendSink.asFlux()
        .doOnNext(bytes -> LOGGER.debug("Sending binary message: {} bytes, ws index {}", bytes.length, indexInPool))
        .map(bytes -> session.binaryMessage(factory -> factory.wrap(bytes)));

    return session.send(
        Flux.merge(textMessages, binaryMessages)
            .doOnSubscribe(s -> LOGGER.debug("Output pipeline subscribed: ws index {}", indexInPool))
            .doOnError(error -> LOGGER.error("Error in send stream: ws index {}", indexInPool, error))
    ).onErrorResume(error -> {
      LOGGER.error("Send pipeline error: ws index {}", indexInPool, error);
      return Mono.empty();
    });
  }

  private void notifySessionOpened() {
    LOGGER.info("WebSocket session opened: ws index {}", indexInPool);
    invokeListener(afterOpenListener, "afterOpenListener");
  }

  // ==================== Message Processing ====================

  private void processTextData(String text) {
    if (messageConsumer != null && text != null) {
      safeExecute(() -> messageConsumer.accept(text), "messageConsumer");
    }
  }

  private void processBinaryData(byte[] bytes) {
    if (bytes != null) {
      // Only emit to receiveSink if no dataConsumer is configured.
      // When dataConsumer is present, callers use callback-based consumption,
      // and emitting to receiveSink would just buffer data that's never consumed,
      // causing memory bloat for large file downloads.
      if (dataConsumer == null) {
        emitToReceiveSink(bytes);
      }
      invokeDataConsumer(bytes);
    }
  }

  private byte[] extractBytes(DataBuffer payload) {
    byte[] bytes = new byte[payload.readableByteCount()];
    payload.read(bytes);
    return bytes;
  }

  private void emitToReceiveSink(byte[] bytes) {
    EmitResult result = receiveSink.tryEmitNext(bytes);
    if (result.isFailure()) {
      logThrottled(lastReceiveSinkWarnTime,
          "Failed to emit received data to sink: ws index {}, result: {}",
          indexInPool, result);
    }
  }

  private void invokeDataConsumer(byte[] bytes) {
    if (dataConsumer != null) {
      safeExecute(() -> dataConsumer.accept(this, bytes), "dataConsumer");
    }
  }

  // ==================== Utility Methods ====================

  private WebSocketClient createWebSocketClient(SslContext sslContext) {
    WebsocketClientSpec.Builder specBuilder = WebsocketClientSpec.builder()
        .maxFramePayloadLength(DEFAULT_MAX_FRAME_PAYLOAD_LENGTH);

    HttpClient httpClient;
    if (sslContext != null) {
      httpClient = HttpClient.create().secure(spec -> spec.sslContext(sslContext));
    } else if ("wss".equalsIgnoreCase(uri.getScheme())) {
      // For wss:// URLs without explicit SSL context, use trust-all (dev/test)
      httpClient = HttpClient.create().secure(spec ->
          spec.sslContext(createDefaultSslContext()));
    } else {
      httpClient = HttpClient.create();
    }

    return new ReactorNettyWebSocketClient(httpClient, () -> specBuilder);
  }

  private static SslContext createDefaultSslContext() {
    try {
      return io.netty.handler.ssl.SslContextBuilder.forClient()
          .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create default SSL context", e);
    }
  }

  private <T> T withActiveConnection(java.util.function.Supplier<T> action, T defaultValue) {
    if (closed.get() || !connectionActive.get()) {
      LOGGER.warn("Cannot perform action - connection not active: ws index {}", indexInPool);
      return defaultValue;
    }
    return action.get();
  }

  private void invokeListener(Consumer<NettyWsClient> listener, String listenerName) {
    if (listener != null) {
      safeExecute(() -> listener.accept(this), listenerName);
    }
  }

  private void safeExecute(Runnable action, String actionName) {
    try {
      action.run();
    } catch (Exception e) {
      LOGGER.error("Error in {}: ws index {}", actionName, indexInPool, e);
    }
  }

  private Mono<Void> emitWithRetry(String message) {
    return Mono.defer(() -> {
          // Fail fast if not connected before first attempt
          if (!isConnected()) {
            return Mono.error(new IllegalStateException("Connection not active"));
          }
          return Mono.<Void>create(sink -> {
            EmitResult result = sendSink.tryEmitNext(message);
            if (result.isSuccess()) {
              sink.success();
              return;
            }
            if (shouldRetryEmit(result) && isConnected()) {
              sink.error(new RetryableEmitException(result));
              return;
            }
            sink.error(new IllegalStateException("Failed to emit message: " + result));
          });
        })
        .retryWhen(Retry.backoff(DEFAULT_SEND_RETRY_MAX, DEFAULT_SEND_RETRY_BACKOFF)
            .filter(error -> error instanceof RetryableEmitException)
            .filter(error -> isConnected()))
        .onErrorResume(error -> {
          logThrottled(lastSendRetryWarnTime,
              "Failed to emit message after retries: ws index {}, error: {}",
              indexInPool, error.getMessage());
          return Mono.empty();
        });
  }

  private boolean shouldRetryEmit(EmitResult result) {
    return result == EmitResult.FAIL_OVERFLOW
           || result == EmitResult.FAIL_NON_SERIALIZED
           || result == EmitResult.FAIL_ZERO_SUBSCRIBER;
  }

  private static final class RetryableEmitException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private RetryableEmitException(EmitResult result) {
      super("Retryable emit failure: " + result);
    }
  }

  private void logThrottled(AtomicLong lastLogTime, String message, Object... args) {
    long now = System.currentTimeMillis();
    long last = lastLogTime.get();
    if (now - last > LOG_THROTTLE_INTERVAL_MS && lastLogTime.compareAndSet(last, now)) {
      LOGGER.warn(message, args);
    }
  }

  private void completeSinks() {
    safeExecute(sendSink::tryEmitComplete, "completing sendSink");
    safeExecute(binarySendSink::tryEmitComplete, "completing binarySendSink");
    safeExecute(receiveSink::tryEmitComplete, "completing receiveSink");
  }

  private static void disposeAndSet(AtomicReference<Disposable> ref, Disposable newValue) {
    Disposable previous = ref.getAndSet(newValue);
    if (previous != null && !previous.isDisposed()) {
      previous.dispose();
    }
  }

  // ==================== Builder ====================

  /**
   * Builder for creating ReactiveWsClient instances with fluent API.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int indexInPool;
    private String clientIP;
    private String pushcaClusterSecret;
    private URI uri;
    private Consumer<String> messageConsumer;
    private BiConsumer<NettyWsClient, byte[]> dataConsumer;
    private Consumer<NettyWsClient> afterOpenListener;
    private Consumer<NettyWsClient> afterCloseListener;
    private SslContext sslContext;
    private Scheduler scheduler = Schedulers.boundedElastic();

    public Builder indexInPool(int indexInPool) {
      this.indexInPool = indexInPool;
      return this;
    }

    public Builder clientIP(String clientIP) {
      this.clientIP = clientIP;
      return this;
    }

    public Builder pushcaClusterSecret(String pushcaClusterSecret) {
      this.pushcaClusterSecret = pushcaClusterSecret;
      return this;
    }

    public Builder uri(URI uri) {
      this.uri = uri;
      return this;
    }

    public Builder messageConsumer(Consumer<String> messageConsumer) {
      this.messageConsumer = messageConsumer;
      return this;
    }

    public Builder dataConsumer(BiConsumer<NettyWsClient, byte[]> dataConsumer) {
      this.dataConsumer = dataConsumer;
      return this;
    }

    public Builder afterOpenListener(Consumer<NettyWsClient> afterOpenListener) {
      this.afterOpenListener = afterOpenListener;
      return this;
    }

    public Builder afterCloseListener(Consumer<NettyWsClient> afterCloseListener) {
      this.afterCloseListener = afterCloseListener;
      return this;
    }

    public Builder sslContext(SslContext sslContext) {
      this.sslContext = sslContext;
      return this;
    }

    public Builder scheduler(Scheduler scheduler) {
      this.scheduler = scheduler;
      return this;
    }

    public NettyWsClient build() {
      return new NettyWsClient(
          indexInPool, clientIP, pushcaClusterSecret, uri,
          messageConsumer, dataConsumer,
          afterOpenListener, afterCloseListener,
          sslContext, scheduler);
    }
  }
}
