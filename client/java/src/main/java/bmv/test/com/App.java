package bmv.test.com;

import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.WebSocketApi;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.tls.SslContextProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;

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
    BiConsumer<WebSocketApi, String> messageConsumer = (ws, msg) -> {
      System.out.println(MessageFormat.format("Message was received {0}", msg));
      lastMessage.set(msg);
    };
    BiConsumer<WebSocketApi, String> messageLogger = (ws, msg) -> System.out.println(msg);
    Consumer<String> acknowledgeConsumer = ack -> {
      System.out.println(MessageFormat.format("Acknowledge was received {0}", ack));
      lastAcknowledge.set(ack);
    };
    BiConsumer<WebSocketApi, Binary> dataConsumer = (ws, binary) -> {
      if (binary.id == null) {
        throw new IllegalStateException("Binary id is empty");
      }
      if (!UUID.nameUUIDFromBytes("TEST".getBytes(StandardCharsets.UTF_8)).toString()
          .equals(binary.id)) {
        throw new IllegalStateException("Wrong binary id");
      }
      if (!"vlc-3.0.11-win64.exe".equals(binary.name)) {
        throw new IllegalStateException("Wrong binary name");
      }
      try {
        FileUtils.writeByteArrayToFile(new File("transferred_" + binary.name), binary.data);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      System.out.println("Binary data was received and stored");
    };
    BiConsumer<WebSocketApi, byte[]> binaryMessageConsumer = (ws, bytes) -> {
      String msg = new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8);
      System.out.println(MessageFormat.format("Binary message was received: {0}", msg));
    };
    try (PushcaWebSocket pushcaWebSocket0 = new PushcaWebSocketBuilder(pushcaApiUrl,
        client0).withAcknowledgeConsumer(acknowledgeConsumer)
        .withMessageConsumer(messageLogger)
        .withBinaryManifestConsumer(data -> System.out.println(toJson(data)))
        .withDataConsumer(dataConsumer)
        .withSslContext(sslContextProvider.getSslContext())
        .build();
        PushcaWebSocket pushcaWebSocket1 = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1).withMessageConsumer(messageConsumer)
            .withBinaryMessageConsumer(binaryMessageConsumer)
            .withAcknowledgeConsumer(acknowledgeConsumer)
            .withSslContext(sslContextProvider.getSslContext())
            .build()) {
      delay(Duration.ofMillis(500));
      lastMessage.set(null);
      delay(Duration.ofSeconds(3));
      //---------------------broadcast message------------------------------------------------------
      ClientFilter filter = new ClientFilter(client0.workSpaceId, null, null, null,
          false, Collections.singletonList(client0));
      pushcaWebSocket0.BroadcastMessage(filter, "Broadcast message test");
      //============================================================================================
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
      //-----------------------------binary with acknowledge----------------------------------------
      File file = new File(
          "C:\\mbugai\\work\\mlx\\pushca-public\\client\\java\\src\\test\\resources\\vlc-3.0.11-win64.exe");
      /*byte[] data = Files.readAllBytes(file.toPath());
      pushcaWebSocket1.sendBinary(client0,
          data,
          "vlc-3.0.11-win64.exe",
          UUID.nameUUIDFromBytes("TEST".getBytes(StandardCharsets.UTF_8)),
          PushcaWebSocket.DEFAULT_CHUNK_SIZE,
          true, false
      );*/
      //============================================================================================
      //-----------------------------binary message-------------------------------------------------
      pushcaWebSocket0.sendBinaryMessage(client1,
          Base64.getEncoder().encode("Binary message test".getBytes(StandardCharsets.UTF_8)));
      //============================================================================================
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
