package bmv.pushca.binary.proxy.pushca.util;

import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.booleanToBytes;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.enumToBytes;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.intToBytes;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.uuidToBytes;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import java.util.UUID;

import bmv.pushca.binary.proxy.pushca.connection.model.BinaryType;
import bmv.pushca.binary.proxy.pushca.model.Datagram;

public final class SendBinaryHelper {

    public static final int BINARY_HEADER_LENGTH = 26;

    public final static int MANIFEST_KEY_CHUNK_INDEX = 1_000_000;

    private SendBinaryHelper() {
    }

    public static Datagram toDatagram(int order, byte[] chunk) {
      return new Datagram(chunk.length, calculateSha256(chunk),null, order);
    }

    public static byte[] toDatagramPrefix(BinaryType binaryType, int destHashCode,
                                          boolean withAcknowledge, UUID binaryId, int order) {
        byte[] prefix = addAll(enumToBytes(binaryType), intToBytes(destHashCode));
        prefix = addAll(prefix, booleanToBytes(withAcknowledge));
        prefix = addAll(prefix, uuidToBytes(binaryId));
        return addAll(prefix, intToBytes(order));
    }
}
