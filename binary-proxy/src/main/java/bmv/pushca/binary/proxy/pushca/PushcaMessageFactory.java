package bmv.pushca.binary.proxy.pushca;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.pushca.model.Command;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class PushcaMessageFactory {

  public static final String MESSAGE_PARTS_DELIMITER = "@@";

  public static final String DEFAULT_RESPONSE = "SUCCESS";

  public enum MessageType {
    ACKNOWLEDGE, BINARY_MANIFEST, CHANNEL_MESSAGE, CHANNEL_EVENT, RESPONSE,
    UPLOAD_BINARY_APPEAL, GATEWAY_REQUEST, PRIVATE_URL_SUFFIX, VALIDATE_PASSWORD_HASH
  }

  public static final TimeBasedEpochGenerator ID_GENERATOR = Generators.timeBasedEpochGenerator();

  public static boolean isValidMessageType(String msg) {
    if (StringUtils.isEmpty(msg)) {
      return false;
    }
    return Arrays.stream(MessageType.values()).anyMatch(v -> v.name().equals(msg));
  }

  public static CommandWithId buildCommandMessage(String externalId, Command command,
      Map<String, Object> args) {
    String id = externalId == null ? ID_GENERATOR.generate().toString() : externalId;
    return new CommandWithId(
        id,
        MessageFormat.format("{0}{1}{2}{3}{4}",
            id, MESSAGE_PARTS_DELIMITER,
            command.name(), MESSAGE_PARTS_DELIMITER, toJson(args))
    );
  }

  public static CommandWithId buildCommandMessage(String externalId, Command command) {
    String id = externalId == null ? ID_GENERATOR.generate().toString() : externalId;
    return new CommandWithId(
        id,
        MessageFormat.format("{0}@@{1}", id, command.name())
    );
  }

  public static final class CommandWithId {

    public final String id;

    public final String commandBody;

    public CommandWithId(String id, String commandBody) {
      this.id = id;
      this.commandBody = commandBody;
    }
  }
}
