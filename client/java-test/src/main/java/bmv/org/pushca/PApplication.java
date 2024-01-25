package bmv.org.pushca;

import static bmv.org.pushca.client.PushcaWebSocket.DEFAULT_CHUNK_SIZE;
import static bmv.org.pushca.client.model.ClientFilter.fromClientWithoutDeviceId;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.delay;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketApi;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UploadBinaryAppeal;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.PChannel;
import bmv.org.pushca.core.PushcaURI;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class PApplication {

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
        "mobile-device",
        "PUSHCA_CLIENT"
    );

    PClient client2 = new PClient(
        "workSpaceGo",
        "clientGo1@test.ee",
        "web-browser",
        "PUSHCA_CLIENT"
    );

    /*SslContextProvider sslContextProvider = new SslContextProvider(
        "C:\\mbugai\\work\\mlx\\pushca\\docker\\conf\\pushca-rc-tls.p12",
        "pwd".toCharArray()
    );*/

    String pushcaApiUrl =
        // "http://localhost:8050";
        //  "https://app-rc.multiloginapp.net/pushca-with-tls-support";
        //"http://push-app-rc.multiloginapp.net:8050";
        "https://app-rc.multiloginapp.net/pushca";
    final String testMessage0 = "test-message-0";
    final String testMessage1 = "test-message-1";
    final String messageId = "1000";
    final byte[] binaryMsg =
        Base64.getEncoder().encode("Binary message test".getBytes(StandardCharsets.UTF_8));
    final byte[] binaryMsg1 =
        Base64.getEncoder().encode("Binary message test1".getBytes(StandardCharsets.UTF_8));
    final AtomicReference<String> lastMessage = new AtomicReference<>();
    BiConsumer<PushcaWebSocketApi, String> messageConsumer = (ws, msg) -> {
      System.out.println(
          MessageFormat.format("{0}: message was received {1}", ws.getClientInfo(), msg));
      lastMessage.set(msg);
    };
    BiConsumer<PushcaWebSocketApi, Binary> dataConsumer = (ws, binary) -> {
      if (binary.id == null) {
        throw new IllegalStateException("Binary id is empty");
      }
      System.out.println("Binary data was received and stored");
    };
    BiConsumer<PushcaWebSocketApi, byte[]> binaryMessageConsumer = (ws, bytes) -> {
      String msg = new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8);
      System.out.println(
          MessageFormat.format("{0}: binary message was received: {1}", ws.getClientInfo(), msg));
    };
    BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer =
        (ws, event) -> System.out.println(
            MessageFormat.format("{0}: channel event was received: {1}", ws.getClientInfo(),
                toJson(event)));
    BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer =
        (ws, channelMessage) -> System.out.println(
            MessageFormat.format("{0}: channel message was received: {1}", ws.getClientInfo(),
                toJson(channelMessage)));
    System.out.println("Web socket test was started");
    try (PushcaWebSocket pushcaWebSocket0 = new PushcaWebSocketBuilder(pushcaApiUrl,
        client0)
        .withMessageConsumer(messageConsumer)
        .withBinaryManifestConsumer((ws, data) -> System.out.println(toJson(data)))
        .withDataConsumer(dataConsumer)
        .withChannelEventConsumer(channelEventConsumer)
        .withChannelMessageConsumer(channelMessageConsumer)
        //.withSslContext(sslContextProvider.getSslContext())
        .build();
        PushcaWebSocket pushcaWebSocket1 = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1).withMessageConsumer(messageConsumer)
            .withBinaryMessageConsumer(binaryMessageConsumer)
            .withChannelEventConsumer(channelEventConsumer)
            .withChannelMessageConsumer(channelMessageConsumer)
            //.withSslContext(sslContextProvider.getSslContext())
            .build();
        PushcaWebSocket pushcaWebSocket1a = new PushcaWebSocketBuilder(pushcaApiUrl,
            client1a).withMessageConsumer(messageConsumer)
            .withBinaryMessageConsumer(binaryMessageConsumer)
            .withChannelEventConsumer(channelEventConsumer)
            .withChannelMessageConsumer(channelMessageConsumer)
            //.withSslContext(sslContextProvider.getSslContext())
            .build()
    ) {
      delay(Duration.ofMillis(500));
      lastMessage.set(null);
      delay(Duration.ofSeconds(3));

      //delay(Duration.ofHours(1));

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
      pushcaWebSocket0.broadcastMessage(filter, "Broadcast message test");
      //============================================================================================
      //---------------------message with acknowledge-----------------------------------------------
      pushcaWebSocket0.sendMessageWithAcknowledge(messageId, client1, testMessage1);
      if (!testMessage1.equals(lastMessage.get())) {
        throw new IllegalStateException("Message was not delivered");
      }
      System.out.println("Message was delivered with acknowledge");
      //============================================================================================
      //-----------------------------binary message-------------------------------------------------
      pushcaWebSocket0.sendBinaryMessage(null, client1, binaryMsg, true);
      //============================================================================================
      //-----------------------------binary with acknowledge----------------------------------------
      String binaryId =
          UUID.nameUUIDFromBytes("vlc-3.0.11-win64-copy.exe".getBytes(StandardCharsets.UTF_8))
              .toString();
      UploadBinaryAppeal appeal = new UploadBinaryAppeal(
          client1, binaryId, "vlc-3.0.11-win64-copy.exe", DEFAULT_CHUNK_SIZE
      );
      PushcaURI uri = new PushcaURI(pushcaWebSocket1.getPusherInstanceId(), 39, appeal);
      String uriStr = uri.toString();
      System.out.println(uriStr);
      PushcaURI uri1 = new PushcaURI(uriStr);
      if (!"vlc-3.0.11-win64-copy.exe".equals(uri1.getUploadBinaryAppeal().binaryName)
          ||
          !pushcaWebSocket1.getPusherInstanceId().equals(uri1.getPusherInstanceId())
          ||
          !binaryId.equals(uri1.getUploadBinaryAppeal().binaryId)) {
        throw new IllegalStateException("Broken Pushca URI");
      }
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
      pushcaWebSocket1.markChannelAsRead(channel0, null);
      channels = pushcaWebSocket1.getChannels(fromClientWithoutDeviceId(client1));
      channel00 = channels.stream()
          .filter(channelWithInfo -> channelWithInfo.channel.id.equals(channel0.id))
          .findFirst().orElse(null);
      if (channel00 == null) {
        throw new IllegalStateException("Channel 0 is not in the list");
      }
      if (!channel00.read) {
        throw new IllegalStateException("Channel 0 was not not marked as read");
      }

      Set<ClientFilter> members = pushcaWebSocket0.getChannelMembers(channel0);
      System.out.println("Channel 0 members: " + toJson(members));

      pushcaWebSocket0.sendMessageToChannel(channel0, "Hello Guys");
      pushcaWebSocket1.sendBinaryMessageToChannel(channel0, binaryMsg1);
      delay(Duration.ofMillis(100));

      channels = pushcaWebSocket1a.getChannels(fromClientWithoutDeviceId(client1a));
      channel00 = channels.stream()
          .filter(channelWithInfo -> channelWithInfo.channel.id.equals(channel0.id))
          .findFirst().orElse(null);
      if (channel00 == null) {
        throw new IllegalStateException("Channel 0 is not in the list");
      }
      if (channel00.counter != 2) {
        throw new IllegalStateException("wrong channel message counter");
      }

      pushcaWebSocket1.removeMeFromChannel(channel0);

      pushcaWebSocket0.broadcastBinaryMessage(fromClientWithoutDeviceId(client1a), binaryMsg);
      pushcaWebSocket0.removeUnusedFilters();

      //=============================send binary====================================================
      File file = new File(
          "C:\\mbugai\\work\\mlx\\pushca-public\\client\\java\\src\\test\\resources\\vlc-3.0.11-win64.exe");
      pushcaWebSocket1.sendBinary(
          client0,
          //client2,
          Files.readAllBytes(file.toPath()),
          "vlc-3.0.11-win64-copy.exe",
          binaryId,
          DEFAULT_CHUNK_SIZE,
          true, null
      );
      /*file = new File("C:\\mbugai\\work\\mlx\\pushca\\Reproducing_multiple_java_headless.mov");
      binaryId = UUID.nameUUIDFromBytes(
              "Reproducing_multiple_java_headless-copy.mov".getBytes(StandardCharsets.UTF_8))
          .toString();
      pushcaWebSocket1.sendBinary(
          client0,
          Files.readAllBytes(file.toPath()),
          "Reproducing_multiple_java_headless-copy.mov",
          binaryId,
          DEFAULT_CHUNK_SIZE,
          true, null
      );*/
      //=================================Upload binary appeal=======================================
      pushcaWebSocket0.sendUploadBinaryAppeal(uri1.toString(), true,
          Arrays.asList(30, 31, 32, 33, 34));
      pushcaWebSocket0.sendUploadBinaryAppeal(uri1.toString(), true, null);
      //============================================================================================

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
