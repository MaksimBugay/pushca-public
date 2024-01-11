package bmv.org.pushca.client.utils;

import static bmv.org.pushca.client.utils.BmvObjectUtils.booleanToBytes;
import static bmv.org.pushca.client.utils.BmvObjectUtils.calculateSha256;
import static bmv.org.pushca.client.utils.BmvObjectUtils.intToBytes;
import static bmv.org.pushca.client.utils.BmvObjectUtils.uuidToBytes;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.model.Datagram;
import bmv.org.pushca.client.model.PClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SendBinaryHelper {

  private SendBinaryHelper() {
  }

  public static BinaryObjectData toBinaryObjectData(String id, String name,
      PClient sender, List<byte[]> chunks, String pusherInstanceId) {
    List<Datagram> datagrams = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      datagrams.add(toDatagram(i, chunks.get(i)));
    }

    return new BinaryObjectData(id, name, datagrams, sender, pusherInstanceId);
  }

  public static Datagram toDatagram(int order, byte[] chunk) {
    Datagram datagram = new Datagram();
    datagram.order = order;
    datagram.size = chunk.length;
    datagram.md5 = calculateSha256(chunk);
    datagram.data = chunk;
    return datagram;
  }

  public static byte[] toDatagramPrefix(UUID id, int order, int clientHashCode,
      boolean withAcknowledge) {
    byte[] prefix = addAll(intToBytes(clientHashCode), booleanToBytes(withAcknowledge));
    prefix = addAll(prefix, uuidToBytes(id));
    return addAll(prefix, intToBytes(order));
  }

}
