package bmv.org.pushcaverifier.pushca;

import bmv.org.pushcaverifier.client.PClient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.MessageFormat;
import java.time.Instant;

public record PushcaMessage(PClient sender, String channelId, String clientMessageId,
                            String transactionId, int index, String body, boolean isBase64,
                            boolean withAck, long sendTime) {

  public PushcaMessage(PClient sender, String channelId, String clientMessageId,
      String transactionId,
      int index, String body, boolean isBase64, boolean withAck) {
    this(sender, channelId, clientMessageId, transactionId, index, body, isBase64, withAck,
        Instant.now().toEpochMilli());
  }

  public PushcaMessage(String transactionId, int index, String body) {
    this(null, null, null, transactionId, index, body, false, false);
  }

  @JsonIgnore
  public String getInternalId() {
    return MessageFormat.format("{0}-{1}", transactionId, String.valueOf(index));
  }
}
