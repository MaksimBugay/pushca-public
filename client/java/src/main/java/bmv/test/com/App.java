package bmv.test.com;

import static bmv.org.pushca.client.model.ClientFilter.fromClientWithoutDeviceId;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.delay;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.WebSocketApi;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.tls.SslContextProvider;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.PChannel;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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
        "clientJava0@test.ee",
        "jmeter",
        "PUSHCA_CLIENT"
    );

    PClient client1 = new PClient(
        "workSpaceMain",
        "clientJava1@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    PClient client1a = new PClient(
        "workSpaceMain",
        "clientJava1@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    PClient client2 = new PClient(
        "workSpaceMain",
        "clientGo10@test.ee",
        "web-browser",
        "PUSHCA_CLIENT"
    );

    SslContextProvider sslContextProvider = new SslContextProvider(
        "C:\\mbugai\\work\\mlx\\pushca\\docker\\conf\\pushca-rc-tls.p12",
        "pwd".toCharArray()
    );

    String pushcaApiUrl =
        "http://localhost:8050";
    //   "https://app-rc.multiloginapp.net/pushca-with-tls-support";
    //"http://push-app-rc.multiloginapp.net:8050";
    //"https://app-rc.multiloginapp.net/pushca";
    final String testMessage0 = "test-message-0";
    final String testMessage1 = "test-message-1";
    final String messageId = "1000";
    final AtomicReference<String> lastMessage = new AtomicReference<>();
    BiConsumer<WebSocketApi, String> messageConsumer = (ws, msg) -> {
      System.out.println(MessageFormat.format("Message was received {0}", msg));
      lastMessage.set(msg);
    };
    BiConsumer<WebSocketApi, String> messageLogger = (ws, msg) -> System.out.println(msg);
    BiConsumer<WebSocketApi, Binary> dataConsumer = (ws, binary) -> {
      if (binary.id == null) {
        throw new IllegalStateException("Binary id is empty");
      }
      try {
        FileUtils.writeByteArrayToFile(new File(binary.name), binary.data);
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
        client0)
        .withMessageConsumer(messageLogger)
        .withBinaryManifestConsumer((ws, data) -> System.out.println(toJson(data)))
        .withDataConsumer(dataConsumer)
        .withChannelEventConsumer((ws, event) -> System.out.println("client0: " + toJson(event)))
        //.withSslContext(sslContextProvider.getSslContext())
        .build();
        PushcaWebSocket pushcaWebSocket1 = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1).withMessageConsumer(messageConsumer)
            .withBinaryMessageConsumer(binaryMessageConsumer)
            .withChannelEventConsumer(
                (ws, event) -> System.out.println("client1: " + toJson(event)))
            //.withSslContext(sslContextProvider.getSslContext())
            .build();
        PushcaWebSocket pushcaWebSocket1a = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1a).withMessageConsumer(messageConsumer)
            .withBinaryMessageConsumer(binaryMessageConsumer)
            .withChannelEventConsumer(
                (ws, event) -> System.out.println("client1a: " + toJson(event)))
            //.withSslContext(sslContextProvider.getSslContext())
            .build()
    ) {
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
      //---------------------broadcast message------------------------------------------------------
      ClientFilter filter = new ClientFilter(client0.workSpaceId, null, null, null,
          false, Collections.singletonList(client0));
      pushcaWebSocket0.BroadcastMessage(filter, "Broadcast message test");
      //============================================================================================
      //---------------------message with acknowledge-----------------------------------------------
      pushcaWebSocket0.sendMessageWithAcknowledge(messageId, client1, testMessage1);
      if (!testMessage1.equals(lastMessage.get())) {
        throw new IllegalStateException("Message was not delivered");
      }
      System.out.println("Message was delivered with acknowledge");
      //============================================================================================
      //-----------------------------binary message-------------------------------------------------
      pushcaWebSocket0.sendBinaryMessage(client1,
          Base64.getEncoder().encode("Binary message test".getBytes(StandardCharsets.UTF_8)), null,
          true);
      //============================================================================================
      //-----------------------------binary with acknowledge----------------------------------------
      File file = new File(
          "C:\\mbugai\\work\\mlx\\pushca-public\\client\\java\\src\\test\\resources\\vlc-3.0.11-win64.exe");
      //file = new File("C:\\mbugai\\work\\mlx\\pushca\\Reproducing_multiple_java_headless.mov");
      byte[] data = Files.readAllBytes(file.toPath());
      /*pushcaWebSocket1.sendBinary(client0,
          data,
          "vlc-3.0.11-win64-copy.exe",
          //"Reproducing_multiple_java_headless-copy.mov",
          UUID.nameUUIDFromBytes("TEST".getBytes(StandardCharsets.UTF_8)),
          PushcaWebSocket.DEFAULT_CHUNK_SIZE,
          true
      );*/
      //============================================================================================
      //=================================Channels===================================================
      PChannel channel0 = pushcaWebSocket0.createChannel(null,
          "happy-pushca-channel-0",
          fromClientWithoutDeviceId(client0),
          fromClientWithoutDeviceId(client2)
      );
      pushcaWebSocket0.addMembersToChannel(channel0, fromClientWithoutDeviceId(client1));
      List<ChannelWithInfo> channels =
          pushcaWebSocket0.getChannels(fromClientWithoutDeviceId(client0));
      ChannelWithInfo channel00 = channels.stream()
          .filter(channelWithInfo -> channelWithInfo.channel.id.equals(channel0.id))
          .findFirst().orElse(null);
      if (channel00 == null) {
        throw new IllegalStateException("Channel 0 is not in the list");
      }
      if (channel00.members.size() != 3) {
        throw new IllegalStateException("Channel 0 has invalid list of members");
      }
      System.out.println("ALL GOOD");
      delay(Duration.ofHours(1));
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
