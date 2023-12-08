package bmv.test.com;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.WebSocketApi;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.tls.SslContextProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class App {

  public static void main(String[] args) throws IOException {
    String message = "Please provide connection pool size ";
    //String poolSize = readLine(message);
    String poolSize = null;
    if (poolSize == null || poolSize.isEmpty()) {
      poolSize = "1";
    }
    System.out.println("Selected poolSize: " + poolSize);

    PClient client0 = new PClient(
        "workSpaceMain",
        "client0@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    PClient client1 = new PClient(
        "workSpaceMain",
        "client1@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    PClient client2 = new PClient(
        "workSpaceMain",
        "client2@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    SslContextProvider sslContextProvider = new SslContextProvider(
        "C:\\mbugai\\work\\mlx\\pushca\\docker\\conf\\pushca-rc-tls.p12",
        "pwd".toCharArray()
    );

    String pushcaApiUrl =
    //    "http://localhost:8050";
        "https://app-rc.multiloginapp.net/pushca-with-tls-support";
    //    "http://push-app-rc.multiloginapp.net:8050";
    //"https://app-rc.multiloginapp.net/pushca";
    final String testMessage0 = "test-message-0";
    final String testMessage1 = "test-message-1";
    final String messageId = "1000";
    final AtomicReference<String> lastMessage = new AtomicReference<>();
    final AtomicReference<String> lastAcknowledge = new AtomicReference<>();
    BiConsumer<WebSocketApi, String> messageConsumer = (ws, msg) -> lastMessage.set(msg);
    BiConsumer<WebSocketApi, String> messageLogger = (ws, msg) -> System.out.println(msg);
    Consumer<String> acknowledgeConsumer = ack -> {
      System.out.println(MessageFormat.format("Acknowledge was received {0}", ack));
      lastAcknowledge.set(ack);
    };
    try (PushcaWebSocket pushcaWebSocket0 = new PushcaWebSocketBuilder(pushcaApiUrl,
        client0).withAcknowledgeConsumer(acknowledgeConsumer)
        .withMessageConsumer(messageLogger)
        .withBinaryManifestConsumer(System.out::println)
        .withSslContext(sslContextProvider.getSslContext())
        .build();
        PushcaWebSocket pushcaWebSocket1 = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1).withMessageConsumer(messageConsumer)
            .withAcknowledgeConsumer(acknowledgeConsumer)
            .withSslContext(sslContextProvider.getSslContext())
            .build()) {
      delay(Duration.ofMillis(500));
      lastMessage.set(null);
      delay(Duration.ofSeconds(3));
      //---------------------simple message---------------------------------------------------------
      pushcaWebSocket0.sendMessage(client1, testMessage0);
      while (lastMessage.get() == null) {
        delay(Duration.ofMillis(100));
      }
      if (!testMessage0.equals(lastMessage.get())) {
        throw new IllegalStateException("Message was not delivered");
      }
      System.out.println("Message was delivered");
      //============================================================================================
      //---------------------message with acknowledge-----------------------------------------------
      pushcaWebSocket0.sendMessageWithAcknowledge(messageId, client1, testMessage1);
      while (lastAcknowledge.get() == null) {
        delay(Duration.ofMillis(100));
      }
      if (!messageId.equals(lastAcknowledge.get())) {
        throw new IllegalStateException("Acknowledge was not received");
      }
      if (!testMessage1.equals(lastMessage.get())) {
        throw new IllegalStateException("Message was not delivered");
      }
      System.out.println("Message was delivered with acknowledge");
      //============================================================================================
      //---------------------message binary with acknowledge----------------------------------------
      pushcaWebSocket1.sendBinary(client0, "HELLO".getBytes(StandardCharsets.UTF_8), true);
      delay(Duration.ofHours(1));
    }
  }

  private static void delay(Duration t) {
    try {
      Thread.sleep(t.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  private static String readLine(String message, Object... args) throws IOException {
    if (System.console() != null) {
      return System.console().readLine(message, args);
    }
    System.out.println(message);
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        System.in));
    return reader.readLine();
  }
}
