package bmv.org.pushca.client;

import static bmv.org.pushca.client.model.WebSocketState.CLOSED;
import static bmv.org.pushca.client.serialization.json.JsonUtility.fromJson;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.calculateSha256;
import static bmv.org.pushca.client.utils.BmvObjectUtils.createAsyncExecutor;
import static bmv.org.pushca.client.utils.BmvObjectUtils.createScheduler;
import static bmv.org.pushca.client.utils.BmvObjectUtils.deepClone;
import static bmv.org.pushca.client.utils.BmvObjectUtils.isEmpty;
import static bmv.org.pushca.client.utils.BmvObjectUtils.toBinary;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toBinaryObjectData;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toDatagramPrefix;
import static bmv.org.pushca.core.Command.ACKNOWLEDGE;
import static bmv.org.pushca.core.Command.ADD_IMPRESSION;
import static bmv.org.pushca.core.Command.ADD_MEMBERS_TO_CHANNEL;
import static bmv.org.pushca.core.Command.CREATE_CHANNEL;
import static bmv.org.pushca.core.Command.GET_CHANNELS;
import static bmv.org.pushca.core.Command.GET_CHANNELS_PUBLIC_INFO;
import static bmv.org.pushca.core.Command.GET_CHANNEL_MEMBERS;
import static bmv.org.pushca.core.Command.GET_IMPRESSION_STAT;
import static bmv.org.pushca.core.Command.MARK_CHANNEL_AS_READ;
import static bmv.org.pushca.core.Command.REFRESH_TOKEN;
import static bmv.org.pushca.core.Command.REGISTER_FILTER;
import static bmv.org.pushca.core.Command.REMOVE_FILTER;
import static bmv.org.pushca.core.Command.REMOVE_ME_FROM_CHANNEL;
import static bmv.org.pushca.core.Command.SEND_BINARY_MANIFEST;
import static bmv.org.pushca.core.Command.SEND_GATEWAY_REQUEST;
import static bmv.org.pushca.core.Command.SEND_GATEWAY_RESPONSE;
import static bmv.org.pushca.core.Command.SEND_MESSAGE;
import static bmv.org.pushca.core.Command.SEND_MESSAGE_TO_CHANNEL;
import static bmv.org.pushca.core.Command.SEND_MESSAGE_WITH_ACKNOWLEDGE;
import static bmv.org.pushca.core.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.org.pushca.core.PushcaMessageFactory.DEFAULT_RESPONSE;
import static bmv.org.pushca.core.PushcaMessageFactory.ID_GENERATOR;
import static bmv.org.pushca.core.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.org.pushca.core.PushcaMessageFactory.isValidMessageType;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.BinaryWithHeader;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.RefreshTokenWsResponse;
import bmv.org.pushca.client.model.UploadBinaryAppeal;
import bmv.org.pushca.client.serialization.json.JsonUtility;
import bmv.org.pushca.client.transformation.BinaryPayloadTransformer;
import bmv.org.pushca.client.utils.BmvObjectUtils;
import bmv.org.pushca.client.utils.SendBinaryHelper.BinaryType;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.Command;
import bmv.org.pushca.core.GetChannelMembersWsResponse;
import bmv.org.pushca.core.GetChannelsWsResponse;
import bmv.org.pushca.core.GetImpressionStatWsResponse;
import bmv.org.pushca.core.PChannel;
import bmv.org.pushca.core.PImpression;
import bmv.org.pushca.core.PushcaMessageFactory;
import bmv.org.pushca.core.PushcaMessageFactory.CommandWithId;
import bmv.org.pushca.core.PushcaMessageFactory.MessageType;
import bmv.org.pushca.core.PushcaURI;
import bmv.org.pushca.core.ResourceImpressionCounters;
import bmv.org.pushca.core.SendMessageToChannelWsResponse;
import bmv.org.pushca.core.SimpleWsResponse;
import bmv.org.pushca.core.gateway.GatewayRequestHeader;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket implements Closeable, PushcaWebSocketApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);
  private static final Duration REFRESH_TOKEN_INTERVAL_MS = Duration.ofMinutes(10);
  private static final Duration KEEP_ALIVE_INTERVAL_MS = Duration.ofSeconds(5);
  public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
  public static final int MAX_REPEAT_ATTEMPT_NUMBER = 3;
  public static final int CALLBACK_TIMEOUT_SEC = 10;
  public static final String INTERNAL_BINARY_FILE_NAME_PATTERN = "binaries//{0}.pushca";
  private final String requestedPusherId;
  private String pusherId;
  private final String pushcaApiUrl;
  private String baseWsUrl;
  private final TokenHolder tokenHolder = new TokenHolder();
  private final PClient client;
  private final String apiKey;
  private final String passwordHash;
  private WebSocketApi webSocket;
  private final ScheduledExecutorService refreshAndCleanupScheduler;
  private final ScheduledExecutorService keepAliveScheduler;
  private final Map<String, BinaryObjectData> binaries = new ConcurrentHashMap<>();
  private final Map<String, CompletableFuture<String>> waitingHall = new ConcurrentHashMap<>();
  private final Map<ClientFilter, Long> filterRegistry = new ConcurrentHashMap<>();
  private final BiConsumer<WebSocketApi, String> wsMessageConsumer;
  private final BiConsumer<WebSocketApi, byte[]> wsDataConsumer;
  private final BinaryPayloadTransformer binaryPayloadTransformer;
  private final BiConsumer<Integer, String> wsOnCloseListener;
  private final SSLContext wsSslContext;
  private final int wsConnectTimeoutMs;
  private final WsConnectionFactory wsConnectionFactory;

  private final ScheduledExecutorService acknowledgeTimeoutScheduler =
      Executors.newScheduledThreadPool(10);

  private final Executor asyncExecutor;

  public static String buildAcknowledgeId(String binaryId, int order) {
    return MessageFormat.format("{0}-{1}", binaryId, java.lang.String.valueOf(order));
  }

  PushcaWebSocket(String pushcaApiUrl, String pusherId,
      String apiKey, PClient client, String passwordHash,
      int connectTimeoutMs,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryWithHeader> unknownDatagramConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer,
      Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors,
      BinaryPayloadTransformer binaryPayloadTransformer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext,
      WsConnectionFactory wsConnectionFactory) {
    this.wsConnectionFactory = wsConnectionFactory;
    this.pushcaApiUrl = pushcaApiUrl;
    this.requestedPusherId = pusherId;
    this.client = client;
    this.apiKey = apiKey;
    this.passwordHash = passwordHash;
    this.wsMessageConsumer =
        (ws, message) -> processMessage(ws, message, messageConsumer, channelEventConsumer,
            channelMessageConsumer, binaryManifestConsumer, gatewayProcessors);
    this.wsDataConsumer =
        (ws, byteBuffer) -> processBinary(ws, byteBuffer, dataConsumer, unknownDatagramConsumer,
            messageConsumer, channelMessageConsumer);
    this.binaryPayloadTransformer = binaryPayloadTransformer;
    this.wsOnCloseListener = onCloseListener;
    this.wsSslContext = sslContext;
    this.wsConnectTimeoutMs = connectTimeoutMs;
    this.asyncExecutor = createAsyncExecutor(10);
    refreshAndCleanupScheduler = createScheduler(
        this::refreshAndCleanupJob,
        REFRESH_TOKEN_INTERVAL_MS,
        Duration.ofSeconds(5)
    );
    keepAliveScheduler = createScheduler(
        this::keepAliveJob,
        KEEP_ALIVE_INTERVAL_MS,
        Duration.ofSeconds(10)
    );
    doSignIn();
  }

  private void doSignIn() {
    OpenConnectionResponse openConnectionResponse = null;
    try {
      openConnectionResponse =
          openConnection(pushcaApiUrl, requestedPusherId, apiKey, client, passwordHash);
    } catch (IOException e) {
      LOGGER.error(
          "Cannot acquire ws connection parameters during sign in attempt: client {}, pusher id {}",
          toJson(client),
          pusherId);
    }

    if (openConnectionResponse != null) {
      this.pusherId = openConnectionResponse.pusherInstanceId;

      URI wsUrl = null;
      try {
        wsUrl = new URI(openConnectionResponse.externalAdvertisedUrl);
      } catch (URISyntaxException e) {
        LOGGER.error("Malformed web socket url: {}", openConnectionResponse.externalAdvertisedUrl);
      }
      if (wsUrl != null) {
        this.baseWsUrl = wsUrl.toString().substring(0, wsUrl.toString().lastIndexOf('/') + 1);
        this.tokenHolder.set(wsUrl.toString().substring(wsUrl.toString().lastIndexOf('/') + 1));
        reConnect();
      }
    }
  }

  private void doSignOut() {
    this.baseWsUrl = null;
    this.tokenHolder.set(null);
    closeWebSocketConnection();
  }

  private void closeWebSocketConnection() {
    if (this.webSocket == null) {
      return;
    }
    try {
      this.webSocket.close();
    } catch (Exception ex) {
      LOGGER.error("Failed close ws connection attempt", ex);
    }
    this.webSocket = null;
  }

  public String getClientInfo() {
    return MessageFormat.format("{0}[{1}]", this.client.accountId, this.client.deviceId);
  }

  public String getPusherInstanceId() {
    return pusherId;
  }

  public void processBinary(WebSocketApi ws, byte[] binary,
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryWithHeader> unknownDatagramConsumer,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer) {

    BinaryWithHeader binaryWithHeader = new BinaryWithHeader(binary);

/*
    LOGGER.debug(MessageFormat.format(
        "binary data received: id {0}, order {1}, with ack {2}", binaryId.toString(),
        String.valueOf(order), String.valueOf(withAcknowledge)));
*/
    //binary message was received
    if (binaryWithHeader.isBinaryMessage()) {
      final String decodedMessage = binaryPayloadTransformer.getDecoder().apply(
          binaryWithHeader.getPayload()
      );
      ChannelMessage channelMessage;
      try {
        channelMessage = JsonUtility.fromJson(decodedMessage, ChannelMessage.class);
      } catch (Exception ex) {
        channelMessage = null;
      }
      if (channelMessage != null) {
        final ChannelMessage chm = channelMessage;
        Optional.ofNullable(channelMessageConsumer).ifPresent(c -> c.accept(this, chm));
      } else {
        Optional.ofNullable(messageConsumer).ifPresent(c -> c.accept(this, decodedMessage));
      }
      if (binaryWithHeader.withAcknowledge) {
        sendAcknowledge(binaryWithHeader.binaryId.toString());
      }
      return;
    }

    BinaryObjectData binaryData =
        binaries.computeIfPresent(binaryWithHeader.binaryId.toString(), (k, v) -> {
          v.fillWithReceivedData(
              binaryWithHeader.order, binaryWithHeader.getPayload());
          return v;
        });
    if (binaryData == null) {
      if (unknownDatagramConsumer != null) {
        unknownDatagramConsumer.accept(this, binaryWithHeader);
        return;
      }
      throw new IllegalStateException("Unknown binary with id = " + binaryWithHeader.binaryId);
    }
    Datagram datagram = binaryData.getDatagram(binaryWithHeader.order);
    if (datagram == null) {
      throw new IllegalArgumentException(
          MessageFormat.format("Unknown datagram: binaryId={0}, order={1}",
              binaryWithHeader.binaryId.toString(),
              String.valueOf(binaryWithHeader.order))
      );
    }
    if (!datagram.md5.equals(calculateSha256(datagram.data))) {
      throw new IllegalArgumentException(
          MessageFormat.format("Md5 validation was not passed: binaryId={0}, order={1}",
              binaryWithHeader.binaryId.toString(),
              String.valueOf(binaryWithHeader.order))
      );
    }
    if (datagram.size != datagram.data.length) {
      throw new IllegalArgumentException(
          MessageFormat.format("Size validation was not passed: binaryId={0}, order={1}",
              binaryWithHeader.binaryId.toString(),
              String.valueOf(binaryWithHeader.order))
      );
    }
    if (binaryWithHeader.withAcknowledge) {
      sendAcknowledge(binaryWithHeader.binaryId, binaryWithHeader.order);
    }
    if (binaryData.isCompleted()) {
      Optional.ofNullable(dataConsumer).ifPresent(
          c -> asyncExecutor.execute(
              () -> c.accept(this, toBinary(binaryData, storeBinary(binaryData)))
          )
      );
      binaries.remove(binaryData.id);
      LOGGER.info("Binary was successfully received: id {}, name {}", binaryData.id,
          binaryData.name);
    }
  }

  private String storeBinary(BinaryObjectData binaryData) {
    byte[] bytes =
        binaryData.getDatagrams().stream().collect(Collectors.toMap(d -> d.order, d -> d.data))
            .entrySet().stream()
            .filter(e -> e.getValue() != null)
            .sorted(Comparator.comparingInt(Entry::getKey))
            .map(Entry::getValue)
            .reduce(ArrayUtils::addAll)
            .orElse(null);

    return storeBinary(binaryData.id, bytes);
  }

  private static String storeBinary(String id, byte[] bytes) {
    if (ArrayUtils.isEmpty(bytes)) {
      return null;
    }
    String fileName = MessageFormat.format(INTERNAL_BINARY_FILE_NAME_PATTERN, id);
    if (Files.exists(Paths.get(fileName))) {
      return fileName;
    }
    try {
      FileUtils.writeByteArrayToFile(new File(fileName), bytes);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot store binary with id = " + id);
    }
    return fileName;
  }

  public void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer,
      Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors) {
    if (StringUtils.isEmpty(inMessage)) {
      return;
    }
    String message = inMessage;
    String[] parts = message.split(MESSAGE_PARTS_DELIMITER);
    if (parts.length > 1) {
      if (isValidMessageType(parts[1])) {
        MessageType type = MessageType.valueOf(parts[1]);
        switch (type) {
          case ACKNOWLEDGE:
            LOGGER.debug(MessageFormat.format("Acknowledge was received: {0}", parts[0]));
            waitingHall.computeIfPresent(parts[0], (key, callback) -> {
              callback.complete(DEFAULT_RESPONSE);
              return callback;
            });
            return;
          case GATEWAY_REQUEST:
            String path = parts[2];
            GatewayRequestHeader header = fromJson(parts[3], GatewayRequestHeader.class);
            byte[] requestPayload = new byte[0];
            if (parts.length == 5) {
              requestPayload = Base64.getDecoder().decode(parts[4]);
            }
            BiFunction<GatewayRequestHeader, byte[], byte[]> gatewayProcessor =
                gatewayProcessors.get(path);
            if (gatewayProcessor != null) {
              byte[] responsePayload = gatewayProcessor.apply(header, requestPayload);
              if (responsePayload == null) {
                responsePayload = new byte[0];
              }
              sendGatewayResponse(parts[0], responsePayload);
            }
            return;
          case BINARY_MANIFEST:
            processBinaryManifest(parts[2], binaryManifestConsumer);
            sendAcknowledge(parts[0]);
            return;
          case CHANNEL_MESSAGE:
            if (channelMessageConsumer != null) {
              channelMessageConsumer.accept(this, fromJson(parts[2], ChannelMessage.class));
            }
            return;
          case CHANNEL_EVENT:
            if (channelEventConsumer != null) {
              channelEventConsumer.accept(this, fromJson(parts[2], ChannelEvent.class));
            }
            return;
          case RESPONSE:
            LOGGER.debug(MessageFormat.format("Response was received: {0}", parts[0]));
            waitingHall.computeIfPresent(parts[0], (key, callback) -> {
              callback.complete(parts.length < 3 ? DEFAULT_RESPONSE : parts[2]);
              return callback;
            });
            return;
          case UPLOAD_BINARY_APPEAL:
            UploadBinaryAppeal appeal = fromJson(parts[2], UploadBinaryAppeal.class);
            asyncExecutor.execute(
                () -> sendBinary(appeal)
            );
            return;
        }
      }
      sendAcknowledge(parts[0]);
      message = parts[1];
    }
    if (messageConsumer != null) {
      messageConsumer.accept(this, message);
    }
  }

  private void processBinaryManifest(String json,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer) {
    BinaryObjectData binaryObjectData = fromJson(json, BinaryObjectData.class);
    if (!binaryObjectData.redOnly) {
      binaries.putIfAbsent(binaryObjectData.id, binaryObjectData);
    }
    if (binaryManifestConsumer != null) {
      binaryManifestConsumer.accept(this, binaryObjectData);
    }
  }

  public synchronized void sendAcknowledge(String id) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("messageId", id);
    CommandWithId cmd = PushcaMessageFactory.buildCommandMessage(id, ACKNOWLEDGE, metaData);
    webSocket.send(cmd.commandBody);
  }

  public void sendGatewayResponse(String id, byte[] response) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("payload", Base64.getEncoder().encodeToString(response));
    webSocket.send(
        PushcaMessageFactory.buildCommandMessage(
            id,
            SEND_GATEWAY_RESPONSE,
            metaData
        ).commandBody
    );
  }

  public void sendAcknowledge(UUID binaryId, int order) {
    String id = buildAcknowledgeId(binaryId.toString(), order);
    sendAcknowledge(id);
  }

  public synchronized void sendMessageWithAcknowledge(String msgId, @NotNull PClient dest,
      boolean preserveOrder, String message) {
    String id = StringUtils.isEmpty(msgId) ? ID_GENERATOR.generate().toString() : msgId;
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("client", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);

    sendCommand(id, SEND_MESSAGE_WITH_ACKNOWLEDGE, metaData);
  }

  public synchronized byte[] sendGatewayRequest(@NotNull ClientFilter dest,
      boolean preserveOrder, String path, byte[] requestPayload) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("receiver", dest);
    metaData.put("preserveOrder", preserveOrder);
    metaData.put("path", path);
    byte[] payload = requestPayload == null ? new byte[0] : requestPayload;
    metaData.put("payload", Base64.getEncoder().encodeToString(payload));

    String responseJson = sendCommand(SEND_GATEWAY_REQUEST, metaData);
    SimpleWsResponse response = fromJson(responseJson, SimpleWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Failed attempt to send gateway request: " + response.error);
    }
    return Base64.getDecoder().decode(response.body);
  }


  public void sendMessageWithAcknowledge(String id, PClient dest, String message) {
    sendMessageWithAcknowledge(id, dest, false, message);
  }

  public synchronized void broadcastMessage(String id, @NotNull ClientFilter dest,
      boolean preserveOrder, String message) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("filter", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);

    sendCommand(SEND_MESSAGE, metaData);
  }

  public void broadcastMessage(@NotNull ClientFilter dest, String message) {
    broadcastMessage(null, dest, false, message);
  }

  public void sendMessage(String id, @NotNull PClient dest, boolean preserveOrder, String message) {
    broadcastMessage(id, new ClientFilter(dest), preserveOrder, message);
  }

  public void sendMessage(PClient dest, String message) {
    sendMessage(null, dest, false, message);
  }

  public void broadcastAsBinaryMessage(@NotNull ClientFilter dest, String message, UUID id) {
    filterRegistry.computeIfAbsent(dest, filter -> {
      registerFilter(filter);
      return Instant.now().toEpochMilli();
    });
    filterRegistry.computeIfPresent(dest, (filter, time) -> Instant.now().toEpochMilli());
    sendAsBinaryMessage(dest.hashCode(), message, id, false);
  }

  public void broadcastAsBinaryMessage(@NotNull ClientFilter dest, String message) {
    broadcastAsBinaryMessage(dest, message, null);
  }

  public void sendAsBinaryMessage(UUID id, @NotNull PClient dest, String message,
      boolean withAcknowledge) {
    sendAsBinaryMessage(dest.hashCode(), message, id, withAcknowledge);
  }

  public synchronized void sendAsBinaryMessage(int destHashCode, String strMessage, UUID id,
      boolean withAcknowledge) {
    byte[] message = binaryPayloadTransformer.getEncoder().apply(strMessage);
    UUID binaryMsgId = (id == null) ? ID_GENERATOR.generate() : id;
    byte[] prefix = toDatagramPrefix(
        BinaryType.BINARY_MESSAGE,
        destHashCode,
        withAcknowledge,
        binaryMsgId,
        0
    );
    byte[] binary = addAll(prefix, message);
    executeWithRepeatOnFailure(
        binaryMsgId.toString(),
        () -> webSocket.send(binary)
    );
  }

  public void sendAsBinaryMessage(@NotNull PClient dest, String message) {
    sendAsBinaryMessage(null, dest, message, false);
  }

  public byte[] loadBinaryById(String binaryId) throws IOException {
    String fileName = MessageFormat.format(INTERNAL_BINARY_FILE_NAME_PATTERN, binaryId);
    File file = new File(fileName);
    return Files.readAllBytes(file.toPath());
  }

  public BinaryObjectData prepareBinaryManifest(byte[] data, String name, String id,
      int chunkSize) {
    String binaryId = id;
    if (StringUtils.isEmpty(binaryId)) {
      binaryId = StringUtils.isEmpty(name) ? ID_GENERATOR.generate().toString() :
          UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
    final String binaryIdFinal = binaryId;
    return binaries.computeIfAbsent(binaryId, v -> {
      byte[] binaryBytes = data;
      if (ArrayUtils.isEmpty(binaryBytes)) {
        try {
          binaryBytes = loadBinaryById(id);
        } catch (IOException e) {
          throw new IllegalArgumentException("Cannot load binary with id = " + id, e);
        }
        if (ArrayUtils.isEmpty(binaryBytes)) {
          throw new IllegalArgumentException(
              MessageFormat.format("Binary with id {0} was not found", id));
        }
      }
      return toBinaryObjectData(
          binaryIdFinal,
          name,
          client,
          BmvObjectUtils.splitToChunks(binaryBytes, chunkSize),
          pusherId
      );
    });
  }

  @Override
  public synchronized void sendBinaryManifest(@NotNull ClientFilter dest,
      BinaryObjectData manifest) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("dest", dest);
    metaData.put("manifest", manifest);

    sendCommand(manifest.id, SEND_BINARY_MANIFEST, metaData);
  }

  public void sendBinary(@NotNull PClient dest, byte[] data) {
    sendBinary(dest, data, false);
  }

  public void sendBinary(@NotNull PClient dest, byte[] data, boolean withAcknowledge) {
    sendBinary(dest, data, null, null, DEFAULT_CHUNK_SIZE, withAcknowledge, null);
  }

  public synchronized void sendBinary(@NotNull PClient dest, byte[] data, String name, String id,
      int chunkSize, boolean withAcknowledge, List<Integer> requestedChunks) {
    BinaryObjectData manifest = prepareBinaryManifest(data, name, id, chunkSize);
    asyncExecutor.execute(() -> storeBinary(manifest.id, data));
    sendBinaryManifest(new ClientFilter(dest),
        deepClone(manifest, BinaryObjectData.class).setRedOnly(false));
    sendBinary(dest, manifest, withAcknowledge, requestedChunks);
  }

  public void sendBinary(UploadBinaryAppeal uploadBinaryAppeal) {
    BinaryObjectData manifest = prepareBinaryManifest(
        null, "", uploadBinaryAppeal.binaryId, uploadBinaryAppeal.chunkSize);
    if (uploadBinaryAppeal.manifestOnly || isEmpty(uploadBinaryAppeal.requestedChunks)) {
      sendBinaryManifest(new ClientFilter(uploadBinaryAppeal.sender),
          deepClone(manifest, BinaryObjectData.class).setRedOnly(false));
    }
    if (uploadBinaryAppeal.manifestOnly) {
      return;
    }
    sendBinary(
        uploadBinaryAppeal.sender,
        manifest, true,
        uploadBinaryAppeal.requestedChunks
    );
  }

  private void sendBinary(PClient dest, BinaryObjectData binaryObjectData, boolean withAcknowledge,
      List<Integer> requestedChunks) {
    UUID binaryId = UUID.fromString(binaryObjectData.id);
    Predicate<Datagram> filter =
        requestedChunks == null ? dgm -> Boolean.TRUE : dgm -> requestedChunks.contains(dgm.order);
    List<Datagram> datagrams = binaryObjectData.getDatagrams().stream()
        .filter(filter)
        .peek(datagram -> datagram.prefix =
            toDatagramPrefix(
                BinaryType.FILE,
                dest.hashCode(), withAcknowledge,
                binaryId, datagram.order
            ))
        .collect(Collectors.toList());

    for (Datagram datagram : datagrams) {
      final String responseId = buildAcknowledgeId(binaryObjectData.id, datagram.order);
      executeWithRepeatOnFailure(
          responseId,
          () -> webSocket.send(addAll(datagram.prefix, datagram.data))
      );
    }
  }

  public void sendUploadBinaryAppeal(ClientFilter owner, String binaryId,
      int chunkSize, boolean manifestOnly, List<Integer> requestedChunks) {
    UploadBinaryAppeal appeal = new UploadBinaryAppeal();
    appeal.owner = owner;
    appeal.binaryId = binaryId;
    appeal.chunkSize = chunkSize;
    appeal.manifestOnly = manifestOnly;
    appeal.requestedChunks = requestedChunks;

    sendUploadBinaryAppeal(appeal);
  }

  public void sendUploadBinaryAppeal(String binaryPushcaURI, boolean withAcknowledge,
      List<Integer> requestedChunks) {
    PushcaURI uri = new PushcaURI(binaryPushcaURI);
    UploadBinaryAppeal appeal = new UploadBinaryAppeal();
    appeal.owner = uri.getUploadBinaryAppeal().owner;
    appeal.binaryId = uri.getUploadBinaryAppeal().binaryId;
    appeal.chunkSize = uri.getUploadBinaryAppeal().chunkSize;
    appeal.manifestOnly = withAcknowledge;
    appeal.requestedChunks = requestedChunks;

    sendUploadBinaryAppeal(appeal);
  }

  private synchronized void sendUploadBinaryAppeal(UploadBinaryAppeal appeal) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("owner", appeal.owner);
    metaData.put("binaryId", appeal.binaryId);
    metaData.put("chunkSize", appeal.chunkSize);
    metaData.put("manifestOnly", appeal.manifestOnly);
    metaData.put("requestedChunks", appeal.requestedChunks);

    String response = sendCommand(SEND_UPLOAD_BINARY_APPEAL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot send upload binary appeal: " + toJson(appeal));
    }
  }

  public synchronized PChannel createChannel(String id, @NotNull String name,
      ClientFilter... filters) {
    String channelId = StringUtils.isEmpty(id) ? ID_GENERATOR.generate().toString() : id;
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", channelId);
    metaData.put("name", name);
    metaData.put("filters", filters);
    String response = sendCommand(CREATE_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot create channel " + name);
    }
    return new PChannel(channelId, name);
  }

  public synchronized void addMembersToChannel(@NotNull PChannel channel, ClientFilter... filters) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("filters", filters);
    String response = sendCommand(ADD_MEMBERS_TO_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot add members to channel " + channel.name);
    }
  }

  public synchronized List<ChannelWithInfo> getChannels(@NotNull ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("filter", filter);
    String responseJson = sendCommand(GET_CHANNELS, metaData);
    GetChannelsWsResponse response = fromJson(responseJson, GetChannelsWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Cannot retrieve list of channels: " + response.error);
    }
    return response.body.channels;
  }

  public synchronized List<ChannelWithInfo> getChannels(@NotNull List<String> ids) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("ids", ids);
    String responseJson = sendCommand(GET_CHANNELS_PUBLIC_INFO, metaData);
    GetChannelsWsResponse response = fromJson(responseJson, GetChannelsWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Cannot retrieve list of channels: " + response.error);
    }
    return response.body.channels;
  }

  public synchronized Set<ClientFilter> getChannelMembers(@NotNull PChannel channel) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    String responseJson = sendCommand(GET_CHANNEL_MEMBERS, metaData);
    GetChannelMembersWsResponse response =
        fromJson(responseJson, GetChannelMembersWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Cannot list channel members: " + response.error);
    }
    return response.body;
  }


  public synchronized void markChannelAsRead(@NotNull PChannel channel, ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("filter", filter);
    String response = sendCommand(MARK_CHANNEL_AS_READ, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot mark channel as read: " + channel.name);
    }
  }

  public synchronized String sendMessageToChannel(@NotNull PChannel channel,
      List<ClientFilter> mentioned, String message) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    if (!isEmpty(mentioned)) {
      metaData.put("mentioned", mentioned);
    }
    metaData.put("message", message);
    String responseJson = sendCommand(SEND_MESSAGE_TO_CHANNEL, metaData);

    SendMessageToChannelWsResponse response =
        fromJson(responseJson, SendMessageToChannelWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException(
          "Cannot send message to channel " + channel.name + ":" + response.error);
    }
    return response.body.messageId;
  }

  public void sendAsBinaryMessageToChannel(@NotNull PChannel channel,
      List<ClientFilter> mentioned, String message) {
    ChannelMessage channelMessage = new ChannelMessage();
    channelMessage.messageId = ID_GENERATOR.generate().toString();
    channelMessage.channelId = channel.id;
    channelMessage.mentioned = mentioned;
    channelMessage.sendTime = Instant.now().toEpochMilli();
    channelMessage.sender = this.client;
    channelMessage.body = message;
    sendAsBinaryMessage(channel.hashCode(), toJson(channelMessage), null, false);
  }

  public synchronized void removeMeFromChannel(@NotNull PChannel channel) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    String response = sendCommand(REMOVE_ME_FROM_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot remove myself from channel " + channel.name);
    }
  }

  public synchronized void addImpression(@NotNull PChannel channel,
      @NotNull PImpression impression) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("impression", impression);
    String response = sendCommand(ADD_IMPRESSION, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot add impression for channel " + channel.name);
    }
  }

  public synchronized void removeImpression(@NotNull PChannel channel,
      @NotNull PImpression impression) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("impression", impression);
    String response = sendCommand(Command.REMOVE_IMPRESSION, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot remove impression in channel " + channel.name);
    }
  }

  public synchronized List<ResourceImpressionCounters> getImpressionStat(
      @NotNull List<String> ids) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("ids", ids);
    String responseJson = sendCommand(GET_IMPRESSION_STAT, metaData);
    GetImpressionStatWsResponse response =
        fromJson(responseJson, GetImpressionStatWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Cannot load impression statistic: " + response.error);
    }
    return response.body;
  }

  public void removeUnusedFilters() {
    filterRegistry.entrySet().stream()
        .filter(entry -> Instant.now().toEpochMilli() - entry.getValue() > Duration.ofHours(1)
            .toMillis())
        .map(Entry::getKey)
        .forEach(this::removeFilter);
  }

  private void registerFilter(ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("filter", filter);
    String response = sendCommand(REGISTER_FILTER, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot register filter " + toJson(filter));
    }
  }

  private void removeFilter(ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("filter", filter);
    String response = sendCommand(REMOVE_FILTER, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot remove filter " + toJson(filter));
    }
    filterRegistry.remove(filter);
  }

  private CompletableFuture<String> registerCallback(String id, String details) {
    CompletableFuture<String> callback = new CompletableFuture<>();
    callback.whenComplete((dId, error) -> waitingHall.remove(dId));
    waitingHall.put(id, callback);
    return CompletableFuture.supplyAsync(() -> {
          try {
            return callback.get(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (ExecutionException | TimeoutException error) {
            LOGGER.error("Failed callback: client {}, id {}, details {}, error {}",
                client.accountId, id, details,
                error.getMessage() == null ? error.getClass().getName() : error.getMessage());
          }
          callback.complete(id);
          return null;
        },
        acknowledgeTimeoutScheduler);
  }

  private String executeWithRepeatOnFailure(String id, Runnable operation) {
    return executeWithRepeatOnFailure(id, operation, "");
  }

  private String executeWithRepeatOnFailure(String id, Runnable operation, String details) {
    Exception error = null;
    for (int i = 0; i < PushcaWebSocket.MAX_REPEAT_ATTEMPT_NUMBER; i++) {
      operation.run();
      try {
        String response = registerCallback(id, details).get();
        if (response != null) {
          return response;
        }
      } catch (Exception e) {
        error = e;
        LOGGER.error("Failed execute operation attempt: details " + details, e);
      }
    }
    throw new WebsocketConnectionIsBrokenException(error);
  }

  private void refreshAndCleanupJob() {
    if (webSocket.isOpen()) {
      refreshToken();
      removeExpiredManifests();
      removeUnusedFilters();
      return;
    }
    if (Instant.now().toEpochMilli() - tokenHolder.getTime()
        > REFRESH_TOKEN_INTERVAL_MS.toMillis()) {
      doSignOut();
    }
  }

  private void keepAliveJob() {
    if (this.webSocket == null || webSocket.getWebSocketState() == CLOSED) {
      closeWebSocketConnection();
      if (this.baseWsUrl == null) {
        doSignIn();
      } else {
        reConnect();
      }
    }
  }

  private void refreshToken() {
    String responseJson = sendCommand(REFRESH_TOKEN, null);
    RefreshTokenWsResponse refreshTokenWsResponse =
        fromJson(responseJson, RefreshTokenWsResponse.class);
    if (StringUtils.isNotEmpty(refreshTokenWsResponse.error)) {
      throw new IllegalStateException(
          "Failed refresh token attempt: " + refreshTokenWsResponse.error);
    }
    if (StringUtils.isNotEmpty(refreshTokenWsResponse.body)) {
      tokenHolder.set(refreshTokenWsResponse.body);
    }
  }

  private void reConnect() {
    LOGGER.debug("Re-connect attempt: client {}, base url {}", client.accountId, baseWsUrl);
    try {
      this.webSocket = wsConnectionFactory.createConnection(
          new URI(baseWsUrl + tokenHolder.get()),
          wsConnectTimeoutMs,
          wsMessageConsumer,
          wsDataConsumer,
          wsOnCloseListener,
          wsSslContext
      );
    } catch (Exception ex) {
      LOGGER.error("Failed open ws connection attempt", ex);
    }
  }

  private OpenConnectionResponse openConnection(String pushcaApiUrl, String pusherId,
      String apiKey, PClient client, String passwordHash) throws IOException {
    URL url = new URL(pushcaApiUrl + "/open-connection");
    URLConnection httpsConn = url.openConnection();
    httpsConn.addRequestProperty("User-Agent", "Mozilla");
    httpsConn.setRequestProperty("Method", "POST");
    httpsConn.setRequestProperty("Content-Type", "application/json");
    httpsConn.setRequestProperty("Accept", "application/json");
    httpsConn.setDoOutput(true);
    OpenConnectionRequest request =
        new OpenConnectionRequest(client, pusherId, apiKey, passwordHash);
    try (OutputStream os = httpsConn.getOutputStream()) {
      byte[] input = toJson(request).getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }
    StringBuilder responseJson = new StringBuilder();
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(httpsConn.getInputStream(), StandardCharsets.UTF_8))) {
      String responseLine;
      while ((responseLine = br.readLine()) != null) {
        responseJson.append(responseLine.trim());
      }
    }
    return fromJson(responseJson.toString(), OpenConnectionResponse.class);
  }

  public String sendCommand(Command command, Map<String, Object> metaData) {
    return sendCommand(null, command, metaData);
  }

  public String sendCommand(String id, Command command, Map<String, Object> metaData) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    CommandWithId cmd = (metaData == null) ? PushcaMessageFactory.buildCommandMessage(id, command) :
        PushcaMessageFactory.buildCommandMessage(id, command, metaData);
    return executeWithRepeatOnFailure(
        cmd.id,
        () -> webSocket.send(cmd.commandBody),
        MessageFormat.format("command {0}, metadata {1}", command.name(), toJson(metaData))
    );
  }

  private void removeExpiredManifests() {
    long now = System.currentTimeMillis();
    List<String> ids = binaries.values().stream()
        .filter(b -> (now - b.created) > Duration.ofMinutes(30).toMillis())
        .map(b -> b.id)
        .collect(Collectors.toList());
    ids.forEach(binaries::remove);
  }

  @Override
  public void close() {
    Optional.ofNullable(refreshAndCleanupScheduler).ifPresent(ExecutorService::shutdown);
    Optional.ofNullable(keepAliveScheduler).ifPresent(ExecutorService::shutdown);
    webSocket.close();
  }
}
