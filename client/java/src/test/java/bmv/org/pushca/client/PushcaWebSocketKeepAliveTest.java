package bmv.org.pushca.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.WebSocketState;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The keep-alive against a local sign-in endpoint and a fake socket: no Pushca server needed.
 */
class PushcaWebSocketKeepAliveTest {

  private HttpServer signIn;
  private final List<FakeSocket> sockets = new CopyOnWriteArrayList<>();
  private final List<String> consumedMessages = new CopyOnWriteArrayList<>();
  // the state of the connections the client opens from now on
  private volatile WebSocketState newSocketState = WebSocketState.OPEN;
  private PushcaWebSocket pushca;

  @BeforeEach
  void signIn() throws IOException {
    signIn = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    signIn.createContext("/pushca/open-connection", exchange -> {
      byte[] body = ("{\"pusherInstanceId\": \"pusher-1\", "
          + "\"externalAdvertisedUrl\": \"wss://pushca.test/token-1\"}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    signIn.start();
    pushca = new PushcaWebSocketBuilder(
        "http://127.0.0.1:" + signIn.getAddress().getPort() + "/pushca", "api-key",
        new PClient("workspace", "account", "device", "APP"), null)
        // the scheduled keep-alive stays out of the way; the tests run it themselves
        .withKeepAliveInterval(Duration.ofHours(1))
        .withWsConnectionFactory(this::fakeSocket)
        .withMessageConsumer((api, message) -> consumedMessages.add(message))
        .build();
  }

  @AfterEach
  void stop() {
    pushca.close();
    signIn.stop(0);
  }

  @Test
  void doesNotPingBeforeThePingIntervalPasses() {
    pushca.keepAliveJob();

    assertTrue(sockets.get(0).sent.isEmpty(), sockets.get(0).sent.toString());
  }

  @Test
  void pingsOncePerPingInterval() {
    FakeSocket socket = sockets.get(0);
    makePingDue();

    pushca.keepAliveJob();
    // the ping was sent a moment ago
    pushca.keepAliveJob();

    assertEquals(1, socket.sent.size(), socket.sent.toString());
    assertTrue(socket.sent.get(0).endsWith("@@PING"), socket.sent.toString());
    assertEquals(1, sockets.size(), "no reconnect");
  }

  @Test
  void theServersPongIsNotPassedOn() {
    FakeSocket socket = sockets.get(0);

    socket.receive("PONG");
    socket.receive("hello");

    assertEquals(List.of("hello"), consumedMessages);
  }

  @Test
  void reconnectsAClosedConnectionWithoutPinging() {
    FakeSocket closed = sockets.get(0);
    closed.state = WebSocketState.CLOSED;

    pushca.keepAliveJob();

    assertTrue(closed.sent.isEmpty());
    assertEquals(2, sockets.size());
    assertEquals(URI.create("wss://pushca.test/token-1"), sockets.get(1).url, "with the stored token");
    assertTrue(sockets.get(1).sent.isEmpty(), sockets.get(1).sent.toString());
  }

  @Test
  void pingsTheReconnectedConnectionWhenThePingIsDue() {
    sockets.get(0).state = WebSocketState.CLOSED;
    makePingDue();

    pushca.keepAliveJob();

    assertEquals(2, sockets.size());
    assertTrue(sockets.get(0).sent.isEmpty());
    assertEquals(1, sockets.get(1).sent.size(), sockets.get(1).sent.toString());
    assertTrue(sockets.get(1).sent.get(0).endsWith("@@PING"));
  }

  @Test
  void aDuePingWaitsForAConnectionThatCannotBeReopened() {
    sockets.get(0).state = WebSocketState.CLOSED;
    newSocketState = WebSocketState.CLOSED;
    makePingDue();

    assertDoesNotThrow(pushca::keepAliveJob);
    assertEquals(2, sockets.size());
    assertTrue(sockets.get(1).sent.isEmpty());

    // reopened by a later run: the ping that was due goes out then
    newSocketState = WebSocketState.OPEN;
    pushca.keepAliveJob();

    assertEquals(3, sockets.size());
    assertEquals(1, sockets.get(2).sent.size(), sockets.get(2).sent.toString());
    assertTrue(sockets.get(2).sent.get(0).endsWith("@@PING"));
  }

  private void makePingDue() {
    pushca.lastIdlePingSentTimestamp.set(
        Instant.now().minus(PushcaWebSocket.PING_INTERVAL).minusSeconds(1).toEpochMilli());
  }

  private WebSocketApi fakeSocket(URI wsUrl, int connectTimeoutMs,
      BiConsumer<WebSocketApi, String> messageConsumer, BiConsumer<WebSocketApi, byte[]> dataConsumer,
      BiConsumer<Integer, String> onCloseListener, SSLContext sslContext) {
    FakeSocket socket = new FakeSocket(wsUrl, messageConsumer, newSocketState);
    sockets.add(socket);
    return socket;
  }

  private static final class FakeSocket implements WebSocketApi {

    final URI url;
    final List<String> sent = new CopyOnWriteArrayList<>();
    volatile WebSocketState state;
    private final BiConsumer<WebSocketApi, String> messageConsumer;

    FakeSocket(URI url, BiConsumer<WebSocketApi, String> messageConsumer, WebSocketState state) {
      this.url = url;
      this.messageConsumer = messageConsumer;
      this.state = state;
    }

    // a text message from the server
    void receive(String message) {
      messageConsumer.accept(this, message);
    }

    @Override
    public void send(byte[] data) {
    }

    @Override
    public void send(String text) {
      sent.add(text);
    }

    @Override
    public void close() {
      state = WebSocketState.CLOSED;
    }

    @Override
    public boolean isOpen() {
      return state == WebSocketState.OPEN;
    }

    @Override
    public WebSocketState getWebSocketState() {
      return state;
    }
  }
}
