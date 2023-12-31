package bmv.org.pushcaverifier.pushca;

import static bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService.MessageType.ACKNOWLEDGE;
import static bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService.MessageType.BINARY_MANIFEST;
import static bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService.MessageType.CHANNEL_MESSAGE;
import static bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService.MessageType.RESPONSE;
import static bmv.org.pushcaverifier.util.JsonUtility.toJson;

import bmv.org.pushcaverifier.client.PClient;
import bmv.org.pushcaverifier.util.DateTimeUtility;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class PushcaMessageFactoryService {

  public enum MessageType {ACKNOWLEDGE, BINARY_MANIFEST, CHANNEL_MESSAGE, RESPONSE}

  private static final TimeBasedEpochGenerator ID_GENERATOR = Generators.timeBasedEpochGenerator();

  public static PushcaMessage build(String id, String body) {
    return build(null, null, id, body);
  }

  public static PushcaMessage build(PClient sender, String id, String body) {
    return build(sender, null, id, body);
  }

  public static PushcaMessage build(PClient sender, String channelId, String id, String body) {
    return build(sender, channelId, id, body, false, false);
  }

  public static PushcaMessage build(PClient sender, String channelId, String id, String body,
      boolean isBase64, boolean withAck) {
    String tx = ID_GENERATOR.generate().toString();
    int index = 0;
    return new PushcaMessage(sender, channelId, id, tx, index, body, isBase64, withAck);
  }

  public static ChannelMessage toChannelMessage(PushcaMessage pushcaMessage) {
    return new ChannelMessage(
        pushcaMessage.sender(),
        pushcaMessage.channelId(),
        pushcaMessage.clientMessageId(),
        pushcaMessage.sendTime(),
        pushcaMessage.body()
    );
  }

  public static PushcaMessage buildChannelWasCreated(String channelId, PClient creator) {
    String body = MessageFormat.format(
        "created at {0} by {1}",
        DateTimeUtility.timeToFormattedString(Instant.now().toEpochMilli()),
        creator.accountId()
    );

    return build(creator, channelId, null, body);
  }

  public static String buildAcknowledge(String messageId, String clientMessageId) {
    String id =
        StringUtils.isEmpty(clientMessageId) ? messageId : clientMessageId;
    return MessageFormat.format("{0}@@{1}", id, ACKNOWLEDGE.name());
  }

  public static String buildBinaryManifest(BinaryObjectMetadata binaryObjectMetadata) {
    return MessageFormat.format("{0}@@{1}", BINARY_MANIFEST.name(), toJson(binaryObjectMetadata));
  }

  public static String buildSimpleMessage(PushcaMessage message) {
    return MessageFormat.format("{0}@@{1}", message.getInternalId(), message.body());
  }

  public static String buildChannelMessage(PushcaMessage message) {
    return MessageFormat.format("{0}@@{1}@@{2}",
        message.getInternalId(), CHANNEL_MESSAGE.name(), toJson(toChannelMessage(message)));
  }

  public static byte[] buildBinaryMessage(PushcaMessage message) {
    if (!message.isBase64()) {
      throw new IllegalArgumentException("Not a binary message: " + toJson(message));
    }
    return Base64.getDecoder().decode(message.body());
  }

  public static <T> String buildResponseMessage(String id, WsResponse<T> response) {
    return MessageFormat.format("{0}@@{1}@@{2}", id, RESPONSE.name(), toJson(response));
  }

  public static String buildResponseMessage(String id) {
    return MessageFormat.format("{0}@@{1}", id, RESPONSE.name());
  }

  public static <T extends CommandMetaData> CommandWithId buildCommandMessage(Command command,
      T args) {
    String id = ID_GENERATOR.generate().toString();
    return new CommandWithId(
        id,
        MessageFormat.format("{0}@@{1}@@{2}", id, command.name(), toJson(args))
    );
  }

  public static CommandWithId buildCommandMessage(Command command, Map<String, Object> args) {
    String id = ID_GENERATOR.generate().toString();
    return new CommandWithId(
        id,
        MessageFormat.format("{0}@@{1}@@{2}", id, command.name(), toJson(args))
    );
  }

  public static CommandWithId buildCommandMessage(Command command) {
    String id = ID_GENERATOR.generate().toString();
    return new CommandWithId(
        id,
        MessageFormat.format("{0}@@{1}", id, command.name())
    );
  }
}
