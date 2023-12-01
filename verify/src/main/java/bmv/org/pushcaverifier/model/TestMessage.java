package bmv.org.pushcaverifier.model;

import static bmv.org.pushcaverifier.util.DateTimeUtility.getCurrentTimestampMs;

import bmv.org.pushcaverifier.client.PClientWithPusherId;
import java.util.UUID;

public record TestMessage(PClientWithPusherId receiver, String id, Long sendTime, String accountId,
                          Long deliveryTime, boolean preserveOrder, int iteration) {

  public TestMessage(PClientWithPusherId receiver) {
    this(
        receiver,
        UUID.randomUUID().toString(),
        getCurrentTimestampMs(),
        null,
        null,
        false,
        0
    );
  }

  public TestMessage(PClientWithPusherId receiver, boolean preserveOrder) {
    this(
        receiver,
        UUID.randomUUID().toString(),
        getCurrentTimestampMs(),
        null,
        null,
        preserveOrder,
        0
    );
  }

  public TestMessage(TestMessage original, String accountId) {
    this(
        original.receiver(),
        original.id(),
        original.sendTime(),
        accountId,
        getCurrentTimestampMs(),
        original.preserveOrder(),
        original.iteration()
    );
  }
}
