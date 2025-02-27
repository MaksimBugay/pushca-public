package bmv.org.pushca.client;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.BinaryWithHeader;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UploadBinaryAppeal;
import bmv.org.pushca.core.ChannelEvent;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.PChannel;
import bmv.org.pushca.core.PImpression;
import bmv.org.pushca.core.ResourceImpressionCounters;
import bmv.org.pushca.core.gateway.GatewayRequestHeader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;

public interface PushcaWebSocketApi {

  String getClientInfo();

  String getPusherInstanceId();

  /**
   * processes an incoming binary message from Pushca
   *
   * @param ws                      - websocket connection object
   * @param binary                  - incoming binary message
   * @param dataConsumer            - external handler of completed binaries
   * @param unknownDatagramConsumer - external handler of datagrams without manifest (usually used
   *                                for torrents like protocol implementation)
   * @param messageConsumer         - external handler of text messages
   * @param channelMessageConsumer  - dedicated external handler of channel messages (object with
   *                                additional info about channel, sender etc.)
   */
  void processBinary(WebSocketApi ws, byte[] binary,
      BiConsumer<PushcaWebSocketApi, Binary> dataConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryWithHeader> unknownDatagramConsumer,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer);

  /**
   * processes an incoming text message from Pushca
   *
   * @param ws                     - websocket connection object
   * @param inMessage              - incoming text message
   * @param messageConsumer        - external handler of text messages
   * @param channelEventConsumer   - external handler of channel related events like created,
   *                               removed etc.
   * @param channelMessageConsumer - dedicated external handler of channel messages (object with
   *                               additional info about channel, sender etc.)
   * @param binaryManifestConsumer - external handler of reveived binary manifests
   * @param gatewayProcessors      - gateway path to response producer mapping
   */
  void processMessage(WebSocketApi ws, String inMessage,
      BiConsumer<PushcaWebSocketApi, String> messageConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelEvent> channelEventConsumer,
      BiConsumer<PushcaWebSocketApi, ChannelMessage> channelMessageConsumer,
      BiConsumer<PushcaWebSocketApi, BinaryObjectData> binaryManifestConsumer,
      Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayProcessors);

  /**
   * acknowledge Pushca about received message (Pushca forwards acknowledge to sender)
   *
   * @param id - message id
   */
  void sendAcknowledge(String id);

  /**
   * acknowledge Pushca about received datagram (Pushca forwards acknowledge to sender)
   *
   * @param binaryId - binary(file) id
   * @param order    - datagram order in binary
   * @param clientHash - client hash
   */
  void sendAcknowledge(UUID binaryId, int order, int clientHash);

  //=================================TEXT MESSAGE API===============================================

  /**
   * send message to some client and wait for acknowledge, if no acknowledge after defined number of
   * send attempts then throw exception
   *
   * @param id            - message id (if null then will be assigned by Pushca)
   * @param dest          - client who should receive a message
   * @param preserveOrder - keep sending order during delivery
   * @param message       - message text
   * @throws WebsocketConnectionIsBrokenException - failed delivery exception
   */
  void sendMessageWithAcknowledge(String id, @NotNull PClient dest, boolean preserveOrder,
      String message) throws WebsocketConnectionIsBrokenException;

  /**
   * short version of sendMessageWithAcknowledge method, that uses defaults: preserveOrder = false
   *
   * @param id      - message id (if null then will be assigned by Pushca)
   * @param dest    - client who should receive a message
   * @param message - message text
   * @throws WebsocketConnectionIsBrokenException - failed delivery exception
   */
  void sendMessageWithAcknowledge(String id, PClient dest, String message)
      throws WebsocketConnectionIsBrokenException;

  /**
   * Send message to all connected clients that met the filtering requirements
   *
   * @param id            - message id (if null then will be assigned by Pushca)
   * @param dest          - filter of receivers
   * @param preserveOrder - keep sending order during delivery
   * @param message       - message text
   */
  void broadcastMessage(String id, @NotNull ClientFilter dest, boolean preserveOrder,
      String message);

  /**
   * short version of broadcastMessage method, that uses defaults: id = null, preserveOrder = false
   *
   * @param dest    - filter of receivers
   * @param message - message text
   */
  void broadcastMessage(@NotNull ClientFilter dest, String message);

  /**
   * Send message to provided client
   *
   * @param id            - message id (if null then will be assigned by Pushca)
   * @param dest          - client
   * @param preserveOrder - keep sending order during delivery
   * @param message       - message text
   */
  void sendMessage(String id, @NotNull PClient dest, boolean preserveOrder, String message);

  /**
   * short version of sendMessage method, that uses defaults: id = null, preserveOrder = false
   *
   * @param dest    - client
   * @param message - message text
   */
  void sendMessage(PClient dest, String message);

  //=============================BINARY MESSAGE API=================================================

  /**
   * Send binary message to all connected clients that met the filtering requirements
   *
   * @param id      - message id (if null then will be assigned by Pushca)
   * @param dest    - filter of receivers
   * @param message - message body
   */
  void broadcastAsBinaryMessage(@NotNull ClientFilter dest, String message, UUID id);

  /**
   * short version of broadcastBinaryMessage method, that uses defaults: id = null
   *
   * @param dest    - filter of receivers
   * @param message - message body
   */
  void broadcastAsBinaryMessage(@NotNull ClientFilter dest, String message);

  /**
   * Send binary message to provided client
   *
   * @param id              - message id (if null then will be assigned by Pushca)
   * @param dest            - client
   * @param message         - message body
   * @param withAcknowledge - wait for acknowledge from receiver
   */
  void sendAsBinaryMessage(UUID id, @NotNull PClient dest, String message, boolean withAcknowledge);

  /**
   * short version of sendBinaryMessage method, that uses defaults: id = null, withAcknowledge =
   * false
   *
   * @param dest    - client
   * @param message - message body
   */
  void sendAsBinaryMessage(@NotNull PClient dest, String message);

  //====================================BINARY(FILE) API============================================
  /**
   * Load binary(file) from some persistent storage
   *
   * @param binaryId - binary identifier
   * @return binary bytes
   */
  byte[] loadBinaryById(String binaryId) throws IOException;

  /**
   * Generate binary manifest and store it in memory cache
   *
   * @param data      - binary data
   * @param name      - file name
   * @param id        - binary id (if null then will be assigned by Pushca)
   * @param chunkSize - pushca client splits file into chunks before sending and sends it chunk by
   *                  chunk
   * @return - binary manifest object
   */
  BinaryObjectData prepareBinaryManifest(byte[] data, String name, String id, int chunkSize);

  /**
   * Send binary manifest object to all connected clients that met the filtering requirements
   *
   * @param dest     - filter of receivers
   * @param manifest - json object with binary metadata and information about all chunks
   */
  void sendBinaryManifest(@NotNull ClientFilter dest, BinaryObjectData manifest);

  /**
   * Send binary (usually file) to provided client
   *
   * @param dest            - client (receiver)
   * @param data            - binary data
   * @param name            - file name
   * @param id              - binary id (if null then will be assigned by Pushca)
   * @param chunkSize       - pushca client splits file into chunks before sending and sends it
   *                        chunk by chunk
   * @param withAcknowledge - wait for acknowledge of previous chunk delivery by receiver before
   *                        send the next chunk
   * @param requestedChunks - identifiers of requested chunks
   */
  void sendBinary(@NotNull PClient dest, byte[] data, String name, String id, int chunkSize,
      boolean withAcknowledge, List<Integer> requestedChunks);

  /**
   * short version of sendBinary method, that uses defaults: name = null, id = null, chunkSize =
   * 1Mb, all chunks
   *
   * @param dest            - client (receiver)
   * @param data            - binary data
   * @param withAcknowledge - wait for acknowledge of previous chunk delivery by receiver before
   *                        send the next chunk
   */
  void sendBinary(@NotNull PClient dest, byte[] data, boolean withAcknowledge);

  /**
   * short version of sendBinary method, that uses defaults: name = null, id = null, chunkSize =
   * 1Mb, withAcknowledge = false, all chunks
   *
   * @param dest - client (receiver)
   * @param data - binary data
   */
  void sendBinary(@NotNull PClient dest, byte[] data);

  /**
   * Send requested binary (file) or listed chunks
   *
   * @param uploadBinaryAppeal - appeal object with all data for sending
   */
  void sendBinary(UploadBinaryAppeal uploadBinaryAppeal);

  /**
   * Ask binary owner to send some binary
   *
   * @param owner           - binary owner
   * @param binaryId        - binary identifier
   * @param chunkSize       - pushca client splits file into chunks before sending and sends it
   *                        chunk by chunk
   * @param manifestOnly    - only binary manifest should be sent, not data
   *                        send the next chunk
   * @param requestedChunks - upload only chunks with provided identifiers, if empty - upload all
   */
  void sendUploadBinaryAppeal(ClientFilter owner, String binaryId, int chunkSize,
      boolean manifestOnly, List<Integer> requestedChunks);

  /**
   * Ask binary owner to send some binary based on shared binary Pushca URI
   *
   * @param binaryPushcaURI - binary Pushca URI
   * @param withAcknowledge - wait for acknowledge of previous chunk delivery by receiver before
   *                        send the next chunk
   * @param requestedChunks - upload only chunks with provided identifiers, if empty - upload all
   */
  void sendUploadBinaryAppeal(String binaryPushcaURI, boolean withAcknowledge,
      List<Integer> requestedChunks);

  //====================================GateWay API=================================================
  /**
   * Generate binary manifest and store it in memory cache
   *
   * @param dest           - filter of receivers
   * @param preserveOrder  - keep sending order during delivery
   * @param path           - gateway path
   * @param requestPayload - gateway request object, protocol implementation is a responsibility of
   *        gateway owner, can be any representation (json, avro, protobuf) converted to byte array
   * @return - gateway response object, protocol implementation is a responsibility of
   *        gateway owner, can be any representation (json, avro, protobuf) converted to byte array
   */
  byte[] sendGatewayRequest(@NotNull ClientFilter dest, boolean preserveOrder, String path,
      byte[] requestPayload);
  //====================================CHANNEL API=================================================

  /**
   * Create a new channel. Channel is a logical unit with group chat features for set of client
   * filters. Every filter defines the only one channel member. It can be several clients under the
   * same filter that provides multi-device support. The most common channel member filter is filter
   * with fixed workspace and application id, omitted device id and account id that actually
   * represent information about member. Every member can access message history that is linked to
   * channel
   *
   * @param id      - channel id (if null then will be assigned by Pushca)
   * @param name    - channel name
   * @param filters - channel members
   * @return - channel object for future communications
   */
  PChannel createChannel(String id, @NotNull String name, ClientFilter... filters);

  /**
   * Add new members into existing channel
   *
   * @param channel - channel object
   * @param filters - new members
   */
  void addMembersToChannel(@NotNull PChannel channel, ClientFilter... filters);

  /**
   * Return list of channels that contains provided filter as a member
   *
   * @param filter - member filter
   * @return list of channels with information about message counter, last update time and read
   * status
   */
  List<ChannelWithInfo> getChannels(@NotNull ClientFilter filter);

  /**
   * Return list of records with channel public info for provided identifiers
   *
   * @param ids - list of channel identifiers
   * @return list of channels with information about message counter, last update time and read
   * status
   */
  List<ChannelWithInfo> getChannels(@NotNull List<String> ids);


  /**
   * Return all channel members
   *
   * @param channel - channel object
   * @return - set of provided channel members
   */
  Set<ClientFilter> getChannelMembers(@NotNull PChannel channel);

  /**
   * Change read status of provided channel to true (already red)
   *
   * @param channel - channel object
   * @param filter  - member filter (if null will be resolved by server but better provide it to
   *                improve performance)
   */
  void markChannelAsRead(@NotNull PChannel channel, ClientFilter filter);

  /**
   * Send text message to channel, all members will receive a notification
   *
   * @param channel   - channel object
   * @param mentioned - channel members that were directly mentioned in message
   * @param message   - message text
   * @return - generated message id
   */
  String sendMessageToChannel(@NotNull PChannel channel, List<ClientFilter> mentioned,
      String message);

  /**
   * Send binary message to channel, all members will receive a notification. Binary message is used
   * to support some custom protocol, that protocol should contain information about sender and
   * destination channel
   *
   * @param channel   - channel object
   * @param mentioned - channel members that were directly mentioned in message
   * @param message   - binary data
   */
  void sendAsBinaryMessageToChannel(@NotNull PChannel channel, List<ClientFilter> mentioned,
      String message);

  /**
   * Remove connected client from some channel, client will stop receive events/messages from
   * channel
   *
   * @param channel - channel object
   */
  void removeMeFromChannel(@NotNull PChannel channel);

  /**
   * Add impression to channel itself or to some message in channel
   *
   * @param channel    - channel object
   * @param impression - impression object
   */
  void addImpression(@NotNull PChannel channel, @NotNull PImpression impression);

  /**
   * Remove previously added impression
   *
   * @param channel    - channel object
   * @param impression - impression object
   */
  void removeImpression(@NotNull PChannel channel, @NotNull PImpression impression);

  /**
   * Get impression counters for provided resources
   *
   * @param ids - list of resource id
   * @return - list of records with impression statistic
   */
  List<ResourceImpressionCounters> getImpressionStat(@NotNull List<String> ids);
}
