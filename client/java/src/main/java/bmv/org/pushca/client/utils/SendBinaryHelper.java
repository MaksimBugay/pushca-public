package bmv.org.pushca.client.utils;

import static bmv.org.pushca.client.utils.BmvObjectUtils.booleanToBytes;
import static bmv.org.pushca.client.utils.BmvObjectUtils.calculateSha256;
import static bmv.org.pushca.client.utils.BmvObjectUtils.intToBytes;
import static bmv.org.pushca.client.utils.BmvObjectUtils.uuidToBytes;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.PClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public final class SendBinaryHelper {

  private SendBinaryHelper() {
  }

  public static BinaryObjectData toBinaryObjectData(PClient dest, UUID id, String name,
      PClient sender, List<byte[]> chunks, String pusherInstanceId, boolean withAcknowledge) {
    UUID binaryId = id;
    if (binaryId == null) {
      binaryId =
          StringUtils.isEmpty(name) ? UUID.randomUUID() : UUID.nameUUIDFromBytes(name.getBytes(
              StandardCharsets.UTF_8));
    }
    List<Datagram> datagrams = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      datagrams.add(toDatagram(binaryId, i, chunks.get(i), dest, withAcknowledge));
    }

    return new BinaryObjectData(binaryId.toString(), name, datagrams, sender, pusherInstanceId);
  }

  public static Datagram toDatagram(UUID binaryId, int order, byte[] chunk, PClient dest,
      boolean withAcknowledge) {
    Datagram datagram = new Datagram();
    datagram.order = order;
    datagram.size = chunk.length;
    datagram.md5 = calculateSha256(chunk);
    datagram.prefix = toDatagramPrefix(binaryId, order, dest, withAcknowledge);
    datagram.data = addAll(datagram.prefix, chunk);

    return datagram;
  }

  public static byte[] toDatagramPrefix(UUID id, int order, PClient dest, boolean withAcknowledge) {
    int clientHashCode = dest.hashCode();
    byte[] prefix = addAll(intToBytes(clientHashCode), booleanToBytes(withAcknowledge));
    prefix = addAll(prefix, uuidToBytes(id));
    return addAll(prefix, intToBytes(order));
  }

}
