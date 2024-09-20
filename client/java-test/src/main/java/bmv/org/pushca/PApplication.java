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
import bmv.org.pushca.client.tls.SslContextProvider;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.PChannel;
import bmv.org.pushca.core.PImpression;
import bmv.org.pushca.core.PushcaURI;
import bmv.org.pushca.core.ResourceImpressionCounters;
import bmv.org.pushca.core.ResourceType;
import bmv.org.pushca.core.gateway.GatewayRequestHeader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;

public class PApplication {

  public static void main(String[] args) throws IOException {
    String message = "Please provide connection pool size ";
    //String poolSize = readLine(message);
    String poolSize = null;
    if (poolSize == null || poolSize.isEmpty()) {
      poolSize = "1";
    }
    System.out.println("Selected poolSize: " + poolSize);

    final String applicationId = "PUSHCA_CLIENT_" + UUID.randomUUID();

    PClient client0 = new PClient(
        "workSpaceMain",
        "clientJava0@test.ee",
        "jmeter",
        applicationId
    );

    PClient client1 = new PClient(
        "workSpaceMain",
        "clientJava1@test.ee",
        UUID.randomUUID().toString(),
        applicationId
    );

    PClient client1a = new PClient(
        "workSpaceMain",
        "clientJava1@test.ee",
        "mobile-device",
        applicationId
    );

    PClient client2 = new PClient(
        "workSpaceGo",
        "clientGo1@test.ee",
        "web-browser",
        applicationId
    );

    SslContextProvider sslContextProvider = new SslContextProvider(
        "C:\\mbugai\\work\\mlx\\pushca\\AWS\\prod\\docker\\conf\\pushca-rc-tls.p12",
        "pushca".toCharArray()
    );
    String apiKey = UUID.randomUUID().toString();
    String pushcaApiUrl = "https://vasilii.prodpushca.com:30443/pushca";
    //"http://localhost:8050";
    final String testMessage0 = "test-message-0";
    final String testMessage1 = "test-message-1";
    final String messageId = "1000";
    final String binaryMsg = "Binary message test";
    final String binaryMsg1 = "Binary message test1";
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
    Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors =
        new HashMap<>();
    gatewayProcessors.put("get-current-time", (header, request) -> getCurrentTime(true));
    gatewayProcessors.put("calculate-sha256",
        (header, request) -> calculateSHA256(new String(request, StandardCharsets.UTF_8)).getBytes(
            StandardCharsets.UTF_8));
    System.out.println("Web socket test was started");
    PushcaWebSocket pushcaWebSocket0 = null;
    PushcaWebSocket pushcaWebSocket1 = null;
    PushcaWebSocket pushcaWebSocket1a = null;
    try {
      pushcaWebSocket0 = new PushcaWebSocketBuilder(pushcaApiUrl,
          apiKey, client0, null)
          .withMessageConsumer(messageConsumer)
          .withBinaryManifestConsumer((ws, data) -> System.out.println(toJson(data)))
          .withDataConsumer(dataConsumer)
          .withChannelEventConsumer(channelEventConsumer)
          .withChannelMessageConsumer(channelMessageConsumer)
          .withGatewayProcessors(gatewayProcessors)
          //.withSslContext(sslContextProvider.getSslContext())
          .build();
      delay(Duration.ofSeconds(1));
      pushcaWebSocket1 = new PushcaWebSocketBuilder(pushcaApiUrl,
          apiKey, client1, null).withMessageConsumer(messageConsumer)
          .withChannelEventConsumer(channelEventConsumer)
          .withChannelMessageConsumer(channelMessageConsumer)
          .withGatewayProcessors(gatewayProcessors)
          //.withSslContext(sslContextProvider.getSslContext())
          .build();
      delay(Duration.ofSeconds(1));
      pushcaWebSocket1a = new PushcaWebSocketBuilder(pushcaApiUrl,
          apiKey, client1a, null).withMessageConsumer(messageConsumer)
          .withChannelEventConsumer(channelEventConsumer)
          .withChannelMessageConsumer(channelMessageConsumer)
          //.withSslContext(sslContextProvider.getSslContext())
          .build();
      delay(Duration.ofSeconds(1));
      //---------------------------------Gateway----------------------------------------------------
      byte[] response = pushcaWebSocket0.sendGatewayRequest(
          new ClientFilter(client1),
          false,
          "get-current-time",
          null
      );
      if (!Arrays.equals(getCurrentTime(true), response)) {
        throw new IllegalStateException("Get current time gateway request failed");
      }
      System.out.println(new String(response, StandardCharsets.UTF_8));
      response = pushcaWebSocket0.sendGatewayRequest(
          new ClientFilter(client1),
          false,
          "calculate-sha256",
          "GatewayTest".getBytes(StandardCharsets.UTF_8)
      );
      if (!Arrays.equals(
          calculateSHA256("GatewayTest").getBytes(StandardCharsets.UTF_8),
          response)
      ) {
        throw new IllegalStateException("Get current time gateway request failed");
      }
      //============================================================================================
      //---------------------simple message---------------------------------------------------------
      lastMessage.set(null);
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
      pushcaWebSocket0.sendAsBinaryMessage(null, client1, binaryMsg, true);
      //============================================================================================
      //-----------------------------binary with acknowledge----------------------------------------
      String binaryId =
          UUID.nameUUIDFromBytes("vlc-3.0.11-win64-copy.exe".getBytes(StandardCharsets.UTF_8))
              .toString();
      UploadBinaryAppeal appeal = new UploadBinaryAppeal(
          new ClientFilter(client1), binaryId, DEFAULT_CHUNK_SIZE
      );
      PushcaURI uri = new PushcaURI(
          pushcaWebSocket1.getPusherInstanceId(), 39,
          "vlc-3.0.11-win64-copy.exe", appeal);
      String uriStr = uri.toString();
      System.out.println(uriStr);
      PushcaURI uri1 = new PushcaURI(uriStr);
      if (!"vlc-3.0.11-win64-copy.exe".equals(uri1.getBinaryName())
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

      String newMessageId = pushcaWebSocket0.sendMessageToChannel(channel0, null, "Hello Guys");
      if (StringUtils.isEmpty(newMessageId)) {
        throw new IllegalStateException("No id in send message to channel response");
      }
      pushcaWebSocket1.sendAsBinaryMessageToChannel(channel0, null, binaryMsg1);
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

      pushcaWebSocket0.broadcastAsBinaryMessage(fromClientWithoutDeviceId(client1a), binaryMsg);
      pushcaWebSocket0.removeUnusedFilters();
      //================mentioned and impressions===================================================
      pushcaWebSocket0.sendMessageToChannel(channel0,
          Collections.singletonList(new ClientFilter(client2)), "Hello @clientGo1@test.ee");
      pushcaWebSocket1.addImpression(channel0,
          new PImpression(channel0.id, ResourceType.CHANNEL, 1));
      List<ResourceImpressionCounters> stat = pushcaWebSocket0.getImpressionStat(
          Collections.singletonList(channel0.id));
      if (stat.get(0).getCounter(1) != 1) {
        throw new IllegalStateException("wrong impressions counter: code 1");
      }
      pushcaWebSocket0.addImpression(channel0,
          new PImpression(channel0.id, ResourceType.CHANNEL, 2));
      pushcaWebSocket1.addImpression(channel0,
          new PImpression(channel0.id, ResourceType.CHANNEL, 2));
      stat = pushcaWebSocket0.getImpressionStat(Collections.singletonList(channel0.id));
      if (stat.get(0).getCounter(2) != 2) {
        throw new IllegalStateException("wrong impressions counter: code 2");
      }
      pushcaWebSocket0.removeImpression(channel0,
          new PImpression(channel0.id, ResourceType.CHANNEL, 2));
      stat = pushcaWebSocket0.getImpressionStat(Collections.singletonList(channel0.id));
      if (stat.get(0).getCounter(2) != 1) {
        throw new IllegalStateException("wrong impressions counter: code 2");
      }

      channels =
          pushcaWebSocket0.getChannels(Arrays.asList(channel0.id, UUID.randomUUID().toString()));
      if (channels.size() != 1) {
        throw new IllegalStateException("wrong channels public info: size != 1");
      }
      if (channels.get(0).counter != 3) {
        throw new IllegalStateException("wrong channels public info: message counter != 3");
      }
      pushcaWebSocket0.removeMeFromChannel(channel0);
      //=============================send binary====================================================
      PClient jsClient = new PClient(
          "cec7abf69bab9f5aa793bd1c0c101e99",
          "anonymous-sharing",
          "86cf2d44-fbaa-4e1c-a317-203ab960a00a",
          "ultimate-file-sharing-listener");
      File file = new File(
          "C:\\mbugai\\work\\mlx\\pushca-public\\client\\java\\src\\test\\resources\\vlc-3.0.11-win64.exe");
      pushcaWebSocket1.sendBinary(
          //jsClient,
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
          false, null
      );*/
      //=================================Upload binary appeal=======================================
      /*pushcaWebSocket0.sendUploadBinaryAppeal(uri1.toString(), true,
          Arrays.asList(30, 31, 32, 33, 34));
      pushcaWebSocket0.sendUploadBinaryAppeal(uri1.toString(), true, null);*/
      //============================================================================================
      System.out.println("ALL GOOD");
      delay(Duration.ofHours(1));
    } finally {
      Optional.ofNullable(pushcaWebSocket0).ifPresent(PushcaWebSocket::close);
      Optional.ofNullable(pushcaWebSocket1).ifPresent(PushcaWebSocket::close);
      Optional.ofNullable(pushcaWebSocket1a).ifPresent(PushcaWebSocket::close);
    }
  }

  private static String calculateSHA256(String input) {
    // Get an instance of MessageDigest for SHA-256
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("SHA-256");

      // Convert the input string to a byte array
      byte[] hashBytes = digest.digest(input.getBytes());

      // Convert the byte array to a hexadecimal string
      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] getCurrentTime(boolean roundToDate) {
    DateTimeFormatter formatter = roundToDate ? DateTimeFormatter.ofPattern("yyyy-MM-dd")
        : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    String formattedNow = now.format(formatter);
    return formattedNow.getBytes(StandardCharsets.UTF_8);
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
