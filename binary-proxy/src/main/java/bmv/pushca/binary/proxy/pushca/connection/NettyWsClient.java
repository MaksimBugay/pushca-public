package bmv.pushca.binary.proxy.pushca.connection;

import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ReferenceCountUtil;
import java.net.URI;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

public class NettyWsClient {

  private final Logger LOGGER = LoggerFactory.getLogger(getClass());

  public static final String CLUSTER_SECRET_HEADER_NAME = "X-Cluster-Secret";

  private final HttpClient httpClient;
  private final int indexInPool;
  private final String clientIP;
  private final String pushcaClusterSecret;
  private final WebSocketClient webSocketClient;
  private final Sinks.Many<String> sendBuffer;
  private final Sinks.Many<DataBuffer> receiveBuffer;
  private final URI uri;
  private Disposable subscription;
  private WebSocketSession session;
  private final Scheduler scheduler;
  private final Consumer<String> messageConsumer;
  private final BiConsumer<NettyWsClient, byte[]> dataConsumer;
  private final Consumer<NettyWsClient> afterOpenListener;
  private final Consumer<NettyWsClient> afterCloseListener;

  public NettyWsClient(int indexInPool,
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
    this.clientIP = clientIP;
    this.pushcaClusterSecret = pushcaClusterSecret;
    this.scheduler = scheduler;
    this.messageConsumer = messageConsumer;
    this.dataConsumer = dataConsumer;
    this.afterCloseListener = afterCloseListener;
    this.afterOpenListener = afterOpenListener;

    WebsocketClientSpec.Builder builder =
        WebsocketClientSpec.builder().maxFramePayloadLength(5 * 1024 * 1024);
    this.httpClient = sslContext == null ? HttpClient.newConnection()
        : HttpClient.newConnection()
            .tcpConfiguration(
                tcpClient -> tcpClient.secure(spec -> spec.sslContext(sslContext)
                )
            );
    this.webSocketClient = new ReactorNettyWebSocketClient(
        httpClient,
        () -> builder
    );

    sendBuffer = Sinks.many().unicast().onBackpressureBuffer();
    receiveBuffer = Sinks.many().unicast().onBackpressureBuffer();
    this.uri = uri;
  }

  public int getIndexInPool() {
    return indexInPool;
  }

  public void openConnection() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Real-IP", clientIP);
    headers.add(CLUSTER_SECRET_HEADER_NAME, pushcaClusterSecret);
    subscription = webSocketClient
        .execute(this.uri, headers, this::handleSession)
        .then(Mono.fromRunnable(this::onClose))
        .subscribeOn(scheduler)
        .subscribe();
  }

  public void disconnect() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
      subscription = null;

      onClose();
    }
  }

  public void send(String message) {
    sendBuffer.tryEmitNext(message);
  }

  public Flux<DataBuffer> receive() {
    return receiveBuffer.asFlux();
  }

  public Optional<WebSocketSession> session() {
    return Optional.ofNullable(session);
  }

  private Mono<Void> handleSession(WebSocketSession session) {
    onOpen(session);

    Mono<Void> input =
        session
            .receive()
            .map(wsData -> {
                  try {
                    if (wsData.getType() == Type.TEXT) {
                      Optional.ofNullable(messageConsumer).ifPresent(l -> l.accept(
                          wsData.getPayloadAsText()));
                    } else if (wsData.getType() == Type.BINARY) {
                      processBinaryPayload(wsData);
                    }
                  } finally {
                    if (wsData.getPayload() instanceof ByteBuf) {
                      ReferenceCountUtil.release(wsData.getPayload());
                    }
                  }
                  return wsData.getPayload();
                }
            )
            .doOnNext(receiveBuffer::tryEmitNext)
            .onErrorResume(throwable -> {
              // Handle error, release resources
              return Mono.empty();
            })
            .then();

    Mono<Void> output =
        session
            .send(
                sendBuffer.asFlux().map(session::textMessage)
            );

    return
        Mono
            .zip(input, output)
            .then();
  }

  private void processBinaryPayload(WebSocketMessage wsData) {
    if (dataConsumer == null) {
      return;
    }
    byte[] bytes = new byte[wsData.getPayload().readableByteCount()];

    try {
        wsData.getPayload().read(bytes); // Read the payload into the byte array
        dataConsumer.accept(this, bytes); // Process the bytes with the data consumer
    } finally {
        // Explicitly release the buffer to avoid memory leaks
        if (wsData.getPayload() instanceof ByteBuf) {
            ReferenceCountUtil.release(wsData.getPayload());
        }
    }
  }

  private void onOpen(WebSocketSession session) {
    this.session = session;
    Optional.ofNullable(afterOpenListener).ifPresent(l -> l.accept(this));
    LOGGER.info("Session opened: ws index {}", indexInPool);
  }

  private void onClose() {
    session = null;
    Optional.ofNullable(afterCloseListener).ifPresent(l -> l.accept(this));
    LOGGER.info("Session closed: ws index {}", indexInPool);
  }
}