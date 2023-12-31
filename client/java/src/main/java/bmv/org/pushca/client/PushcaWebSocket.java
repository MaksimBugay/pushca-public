package bmv.org.pushca.client;

import static bmv.org.pushca.client.model.WebSocketState.CLOSING;
import static bmv.org.pushca.client.model.WebSocketState.NOT_YET_CONNECTED;
import static bmv.org.pushca.client.model.WebSocketState.PERMANENTLY_CLOSED;
import static bmv.org.pushca.client.serialization.json.JsonUtility.fromJson;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.calculateSha256;
import static bmv.org.pushca.client.utils.BmvObjectUtils.createScheduler;
import static bmv.org.pushca.client.utils.BmvObjectUtils.toBinary;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toBinaryObjectData;
import static bmv.org.pushca.client.utils.SendBinaryHelper.toDatagramPrefix;
import static bmv.org.pushca.core.Command.ACKNOWLEDGE;
import static bmv.org.pushca.core.Command.ADD_MEMBERS_TO_CHANNEL;
import static bmv.org.pushca.core.Command.CREATE_CHANNEL;
import static bmv.org.pushca.core.Command.GET_CHANNELS;
import static bmv.org.pushca.core.Command.MARK_CHANNEL_AS_READ;
import static bmv.org.pushca.core.Command.REFRESH_TOKEN;
import static bmv.org.pushca.core.Command.REGISTER_FILTER;
import static bmv.org.pushca.core.Command.REMOVE_FILTER;
import static bmv.org.pushca.core.Command.REMOVE_ME_FROM_CHANNEL;
import static bmv.org.pushca.core.Command.SEND_BINARY_MANIFEST;
import static bmv.org.pushca.core.Command.SEND_MESSAGE;
import static bmv.org.pushca.core.Command.SEND_MESSAGE_TO_CHANNEL;
import static bmv.org.pushca.core.Command.SEND_MESSAGE_WITH_ACKNOWLEDGE;
import static bmv.org.pushca.core.PushcaMessageFactory.DEFAULT_RESPONSE;
import static bmv.org.pushca.core.PushcaMessageFactory.ID_GENERATOR;
import static bmv.org.pushca.core.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.org.pushca.core.PushcaMessageFactory.isValidMessageType;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.OpenConnectionRequest;
import bmv.org.pushca.client.model.OpenConnectionResponse;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.RefreshTokenWsResponse;
import bmv.org.pushca.client.model.UnknownDatagram;
import bmv.org.pushca.client.model.WebSocketState;
import bmv.org.pushca.client.utils.BmvObjectUtils;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.Command;
import bmv.org.pushca.core.GetChannelsWsResponse;
import bmv.org.pushca.core.PChannel;
import bmv.org.pushca.core.PushcaMessageFactory;
import bmv.org.pushca.core.PushcaMessageFactory.CommandWithId;
import bmv.org.pushca.core.PushcaMessageFactory.MessageType;
import com.sun.istack.internal.NotNull;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushcaWebSocket implements Closeable, PushcaWebSocketApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(PushcaWebSocket.class);
  private static final long REFRESH_TOKEN_INTERVAL_MS = Duration.ofMinutes(10).toMillis();
  private static final List<Integer> RECONNECT_INTERVALS = Arrays.asList(
      0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597
  );
  public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
  public static final int MAX_REPEAT_ATTEMPT_NUMBER = 3;
  public static final int CALLBACK_TIMEOUT_SEC = 10;
  private final String pusherId;

  private final String baseWsUrl;

  private final AtomicReference<String> tokenHolder = new AtomicReference<>();

  private final PClient client;

  private WebSocketApi webSocket;

  private final AtomicReference<WebSocketState> stateHolder = new AtomicReference<>();

  private ScheduledExecutorService scheduler;

  private final AtomicLong lastTokenRefreshTime = new AtomicLong();

  private final AtomicInteger reConnectIndex = new AtomicInteger();

  private final Map<String, BinaryObjectData> binaries = new ConcurrentHashMap<>();

  private final Map<String, CompletableFuture<String>> waitingHall = new ConcurrentHashMap<>();
  private final Map<ClientFilter, Long> filterRegistry = new ConcurrentHashMap<>();
  private final BiConsumer<WebSocketApi, String> wsMessageConsumer;
  private final BiConsumer<WebSocketApi, byte[]> wsDataConsumer;
  private final BiConsumer<Integer, String> wsOnCloseListener;
  private final SSLContext wsSslContext;
  private final int wsConnectTimeoutMs;

  private final WsConnectionFactory wsConnectionFactory;

  private final ScheduledExecutorService acknowledgeTimeoutScheduler =
      Executors.newScheduledThreadPool(10);

  public static String buildAcknowledgeId(String binaryId, int order) {
    return MessageFormat.format("{0}-{1}", binaryId, String.valueOf(order));
  }

  PushcaWebSocket(String pushcaApiUrl, String pusherId, PClient client, int connectTimeoutMs,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, byte[]> binaryMessageConsumer,
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer,
      BiConsumer<PushcaWebSocketApi, UnknownDatagram> unknownDatagramConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer,
      BiConsumer<Integer, String> onCloseListener,
      SSLContext sslContext,
      WsConnectionFactory wsConnectionFactory) {
    this.wsConnectionFactory = wsConnectionFactory;
    this.client = client;
    this.wsMessageConsumer =
        (ws, message) -> processMessage(ws, message, messageConsumer, channelEventConsumer,
            channelMessageConsumer, binaryManifestConsumer);
    this.wsDataConsumer =
        (ws, byteBuffer) -> processBinary(ws, byteBuffer, dataConsumer, unknownDatagramConsumer,
            binaryMessageConsumer);
    this.wsOnCloseListener = onCloseListener;
    this.wsSslContext = sslContext;
    this.wsConnectTimeoutMs = connectTimeoutMs;
    OpenConnectionResponse openConnectionResponse = null;
    try {
      openConnectionResponse = openConnection(pushcaApiUrl, pusherId, client);
    } catch (IOException e) {
      LOGGER.error("Cannot open websocket connection: client {}, pusher id {}", toJson(client),
          pusherId);
      this.stateHolder.set(WebSocketState.CLOSED);
    }

    if (openConnectionResponse != null) {
      this.pusherId = openConnectionResponse.pusherInstanceId;

      URI wsUrl = null;
      try {
        wsUrl = new URI(openConnectionResponse.externalAdvertisedUrl);
      } catch (URISyntaxException e) {
        LOGGER.error("Malformed web socket url: {}", openConnectionResponse.externalAdvertisedUrl);
        this.stateHolder.set(WebSocketState.CLOSED);
      }

      if (wsUrl != null) {
        this.baseWsUrl = wsUrl.toString().substring(0, wsUrl.toString().lastIndexOf('/') + 1);
        this.tokenHolder.set(wsUrl.toString().substring(wsUrl.toString().lastIndexOf('/') + 1));
        this.webSocket = this.wsConnectionFactory.createConnection(wsUrl,
            this.wsConnectTimeoutMs,
            this.wsMessageConsumer,
            this.wsDataConsumer,
            this.wsOnCloseListener,
            this.wsSslContext);
        scheduler = createScheduler(
            this::keepAliveJob,
            Duration.ofSeconds(1),
            Duration.ofSeconds(2)
        );
        LOGGER.debug("Connection attributes: baseUrl {}, token {}", baseWsUrl, tokenHolder);
      } else {
        this.baseWsUrl = null;
      }
    } else {
      this.pusherId = pusherId;
      this.baseWsUrl = null;
    }
  }

  public String getClientInfo() {
    return MessageFormat.format("{0}[{1}]", this.client.accountId, this.client.deviceId);
  }

  public void processBinary(WebSocketApi ws, byte[] binary,
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer,
      BiConsumer<PushcaWebSocketApi, UnknownDatagram> unknownDatagramConsumer,
      BiConsumer<PushcaWebSocketApi, byte[]> binaryMessageConsumer) {
    final int clientHash = BmvObjectUtils.bytesToInt(
        Arrays.copyOfRange(binary, 0, 4)
    );
    boolean withAcknowledge = BmvObjectUtils.bytesToBoolean(
        Arrays.copyOfRange(binary, 4, 5)
    );
    final UUID binaryId = BmvObjectUtils.bytesToUuid(Arrays.copyOfRange(binary, 5, 21));
    final int order = BmvObjectUtils.bytesToInt(Arrays.copyOfRange(binary, 21, 25));

/*
    LOGGER.debug(MessageFormat.format(
        "binary data received: id {0}, order {1}, with ack {2}", binaryId.toString(),
        String.valueOf(order), String.valueOf(withAcknowledge)));
*/
    //binary message was received
    if (Integer.MAX_VALUE == order) {
      Optional.ofNullable(binaryMessageConsumer)
          .ifPresent(c -> c.accept(this, Arrays.copyOfRange(binary, 25, binary.length)));
      if (withAcknowledge) {
        sendAcknowledge(binaryId.toString());
      }
      return;
    }

    BinaryObjectData binaryData = binaries.computeIfPresent(binaryId.toString(), (k, v) -> {
      v.fillWithReceivedData(order, Arrays.copyOfRange(binary, 25, binary.length));
      return v;
    });
    if (binaryData == null) {
      if (unknownDatagramConsumer != null) {
        unknownDatagramConsumer.accept(this, new UnknownDatagram(
            binaryId,
            Arrays.copyOfRange(binary, 0, 25),
            order,
            Arrays.copyOfRange(binary, 25, binary.length)
        ));
        return;
      }
      throw new IllegalStateException("Unknown binary with id = " + binaryId);
    }
    Datagram datagram = binaryData.getDatagram(order);
    if (datagram == null) {
      throw new IllegalArgumentException(
          MessageFormat.format("Unknown datagram: binaryId={0}, order={1}", binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (!datagram.md5.equals(calculateSha256(datagram.data))) {
      throw new IllegalArgumentException(
          MessageFormat.format("Md5 validation was not passed: binaryId={0}, order={1}",
              binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (datagram.size != datagram.data.length) {
      throw new IllegalArgumentException(
          MessageFormat.format("Size validation was not passed: binaryId={0}, order={1}",
              binaryId.toString(),
              String.valueOf(order))
      );
    }
    if (withAcknowledge) {
      sendAcknowledge(binaryId, order);
    }
    if (binaryData.isCompleted()) {
      Optional.ofNullable(dataConsumer).ifPresent(c -> c.accept(this, toBinary(binaryData)));
      binaries.remove(binaryData.getBinaryId());
      LOGGER.info("Binary was successfully received: id {}, name {}", binaryData.getBinaryId(),
          binaryData.name);
    }
  }

  public void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer) {
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
            waitingHall.computeIfPresent(parts[0], (key, callback) -> {
              callback.complete(parts.length < 3 ? DEFAULT_RESPONSE : parts[2]);
              return callback;
            });
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
      binaries.putIfAbsent(binaryObjectData.getBinaryId(), binaryObjectData);
    }
    if (binaryManifestConsumer != null) {
      binaryManifestConsumer.accept(this, binaryObjectData);
    }
  }

  public void sendAcknowledge(String id) {
    if (!webSocket.isOpen()) {
      throw new IllegalStateException("Web socket connection is broken");
    }
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("messageId", id);
    CommandWithId cmd = PushcaMessageFactory.buildCommandMessage(ACKNOWLEDGE, metaData);
    webSocket.send(cmd.commandBody);
  }

  public void sendAcknowledge(UUID binaryId, int order) {
    String id = buildAcknowledgeId(binaryId.toString(), order);
    sendAcknowledge(id);
  }

  public void sendMessageWithAcknowledge(String msgId, PClient dest, boolean preserveOrder,
      String message) {
    String id = StringUtils.isEmpty(msgId) ? ID_GENERATOR.generate().toString() : msgId;
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("client", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);

    sendCommand(id, SEND_MESSAGE_WITH_ACKNOWLEDGE, metaData);
  }

  public void sendMessageWithAcknowledge(String id, PClient dest, String message) {
    sendMessageWithAcknowledge(id, dest, false, message);
  }

  public void broadcastMessage(String id, ClientFilter dest, boolean preserveOrder,
      String message) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("filter", dest);
    metaData.put("sender", client);
    metaData.put("message", message);
    metaData.put("preserveOrder", preserveOrder);

    sendCommand(SEND_MESSAGE, metaData);
  }

  public void broadcastMessage(ClientFilter dest, String message) {
    broadcastMessage(null, dest, false, message);
  }

  public void sendMessage(String id, PClient dest, boolean preserveOrder, String message) {
    broadcastMessage(id, new ClientFilter(dest), preserveOrder, message);
  }

  public void sendMessage(PClient dest, String message) {
    sendMessage(null, dest, false, message);
  }

  public void broadcastBinaryMessage(ClientFilter dest, byte[] message, UUID id) {
    filterRegistry.computeIfAbsent(dest, filter -> {
      registerFilter(filter);
      return Instant.now().toEpochMilli();
    });
    filterRegistry.computeIfPresent(dest, (filter, time) -> Instant.now().toEpochMilli());
    sendBinaryMessage(dest.hashCode(), message, id, false);
  }

  public void broadcastBinaryMessage(ClientFilter dest, byte[] message) {
    broadcastBinaryMessage(dest, message, null);
  }

  public void sendBinaryMessage(UUID id, PClient dest, byte[] message, boolean withAcknowledge) {
    sendBinaryMessage(dest.hashCode(), message, id, withAcknowledge);
  }

  public void sendBinaryMessage(int destHashCode, byte[] message, UUID id,
      boolean withAcknowledge) {
    UUID binaryMsgId = (id == null) ? ID_GENERATOR.generate() : id;
    int order = Integer.MAX_VALUE;
    byte[] prefix = toDatagramPrefix(binaryMsgId, order, destHashCode, withAcknowledge);
    byte[] binary = addAll(prefix, message);
    if (withAcknowledge) {
      executeWithRepeatOnFailure(
          binaryMsgId.toString(),
          () -> webSocket.send(binary)
      );
    } else {
      webSocket.send(binary);
    }
  }

  public void sendBinaryMessage(PClient dest, byte[] message) {
    sendBinaryMessage(null, dest, message, false);
  }

  @Override
  public void sendBinaryManifest(ClientFilter dest, BinaryObjectData manifest) {
    sendBinaryManifest(dest, manifest, true);
  }

  private void sendBinaryManifest(ClientFilter dest, BinaryObjectData manifest, boolean readOnly) {
    manifest.redOnly = readOnly;
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("dest", dest);
    metaData.put("manifest", manifest);

    sendCommand(manifest.id, SEND_BINARY_MANIFEST, metaData);
  }

  public void sendBinary(PClient dest, byte[] data) {
    sendBinary(dest, data, false);
  }

  public void sendBinary(PClient dest, byte[] data, boolean withAcknowledge) {
    sendBinary(dest, data, null, null, DEFAULT_CHUNK_SIZE, withAcknowledge);
  }

  public BinaryObjectData sendBinary(PClient dest, byte[] data, String name, UUID id, int chunkSize,
      boolean withAcknowledge) {
    BinaryObjectData binaryObjectData = toBinaryObjectData(
        dest,
        id,
        name,
        client,
        BmvObjectUtils.splitToChunks(data, chunkSize),
        pusherId,
        withAcknowledge
    );
    sendBinaryManifest(new ClientFilter(dest), binaryObjectData, false);
    sendBinary(binaryObjectData, withAcknowledge, null);
    return binaryObjectData;
  }

  public void sendBinary(String binaryId, boolean withAcknowledge, List<String> requestedIds) {
    BinaryObjectData binaryObjectData = binaries.get(binaryId);
    if (binaryObjectData == null) {
      LOGGER.error("Unknown binary with id = {}", binaryId);
      return;
    }
    sendBinary(binaryObjectData, withAcknowledge, requestedIds);
  }

  private void sendBinary(BinaryObjectData binaryObjectData, boolean withAcknowledge,
      List<String> requestedIds) {
    Predicate<Datagram> filter =
        requestedIds == null ? dgm -> Boolean.TRUE : dgm -> requestedIds.contains(
            buildAcknowledgeId(binaryObjectData.id, dgm.order)
        );
    List<Datagram> datagrams = binaryObjectData.getDatagrams().stream()
        .filter(filter)
        .collect(Collectors.toList());
    if (withAcknowledge) {
      for (Datagram datagram : datagrams) {
        executeWithRepeatOnFailure(
            buildAcknowledgeId(binaryObjectData.id, datagram.order),
            () -> webSocket.send(datagram.data)
        );
      }
    } else {
      datagrams.forEach(datagram -> webSocket.send(datagram.data));
    }
  }

  public PChannel createChannel(String id, @NotNull String name, ClientFilter... filters) {
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

  public void addMembersToChannel(@NotNull PChannel channel, ClientFilter... filters) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("filters", filters);
    String response = sendCommand(ADD_MEMBERS_TO_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot add members to channel " + channel.name);
    }
  }

  public List<ChannelWithInfo> getChannels(ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("filter", filter);
    String responseJson = sendCommand(GET_CHANNELS, metaData);
    GetChannelsWsResponse response = fromJson(responseJson, GetChannelsWsResponse.class);
    if (StringUtils.isNotEmpty(response.error)) {
      throw new IllegalStateException("Cannot retrieve list of channels: " + response.error);
    }
    return response.body.channels;
  }

  public void markChannelAsRead(@NotNull PChannel channel, ClientFilter filter) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("filter", filter);
    String response = sendCommand(MARK_CHANNEL_AS_READ, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot mark channel as read: " + channel.name);
    }
  }

  public void sendMessageToChannel(@NotNull PChannel channel, String message) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    metaData.put("message", message);
    String response = sendCommand(SEND_MESSAGE_TO_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot send message to channel " + channel.name);
    }
  }

  public void sendBinaryMessageToChannel(@NotNull PChannel channel, byte[] message) {
    sendBinaryMessage(channel.hashCode(), message, null, false);
  }

  public void removeMeFromChannel(@NotNull PChannel channel) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("channel", channel);
    String response = sendCommand(REMOVE_ME_FROM_CHANNEL, metaData);
    if (!"SUCCESS".equals(response)) {
      throw new IllegalStateException("Cannot remove myself from channel " + channel.name);
    }
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

  private void keepAliveJob() {
    if (stateHolder.get() == PERMANENTLY_CLOSED) {
      return;
    }
    stateHolder.set(webSocket.getWebSocketState());
    if (webSocket.isOpen()) {
      reConnectIndex.set(0);
      if (lastTokenRefreshTime.get() == 0
          || System.currentTimeMillis() - lastTokenRefreshTime.get() > REFRESH_TOKEN_INTERVAL_MS) {
        refreshToken();
        removeExpiredManifests();
        removeUnusedFilters();
      }
      return;
    }
    if (webSocket.getWebSocketState() == CLOSING
        || webSocket.getWebSocketState() == NOT_YET_CONNECTED) {
      return;
    }
    //re-connect attempt
    if (reConnectIndex.get() > 2000) {
      stateHolder.set(PERMANENTLY_CLOSED);
      LOGGER.error("Web socket was permanently closed: client {}", toJson(client));
      return;
    }
    if (RECONNECT_INTERVALS.contains(reConnectIndex.getAndIncrement())) {
      reConnect();
    }
  }

  private void refreshToken() {
    lastTokenRefreshTime.set(System.currentTimeMillis());
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
    try {
      webSocket = wsConnectionFactory.createConnection(
          new URI(baseWsUrl + tokenHolder.get()),
          wsConnectTimeoutMs,
          wsMessageConsumer,
          wsDataConsumer,
          wsOnCloseListener,
          wsSslContext
      );
    } catch (URISyntaxException e) {
      LOGGER.error("Malformed web socket url: {}", baseWsUrl + tokenHolder.get());
    }
  }

  private OpenConnectionResponse openConnection(String pushcaApiUrl, String pusherId,
      PClient client) throws IOException {
    URL url = new URL(pushcaApiUrl + "/open-connection");
    URLConnection httpsConn = url.openConnection();
    httpsConn.addRequestProperty("User-Agent", "Mozilla");
    httpsConn.setRequestProperty("Method", "POST");
    httpsConn.setRequestProperty("Content-Type", "application/json");
    httpsConn.setRequestProperty("Accept", "application/json");
    httpsConn.setDoOutput(true);
    OpenConnectionRequest request = new OpenConnectionRequest(client, pusherId);
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
    CommandWithId cmd = (metaData == null) ? PushcaMessageFactory.buildCommandMessage(command) :
        PushcaMessageFactory.buildCommandMessage(command, metaData);
    return executeWithRepeatOnFailure(
        StringUtils.isEmpty(id) ? cmd.id : id,
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
    Optional.ofNullable(scheduler).ifPresent(ExecutorService::shutdown);
    webSocket.close();
  }
}
