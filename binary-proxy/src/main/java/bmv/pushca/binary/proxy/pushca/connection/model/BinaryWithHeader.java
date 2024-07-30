package bmv.pushca.binary.proxy.pushca.connection.model;

import bmv.pushca.binary.proxy.pushca.BmvObjectUtils;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import java.util.Arrays;
import java.util.UUID;

public record BinaryWithHeader(BinaryType binaryType, int clientHash, boolean withAcknowledge,
                               UUID binaryId, int order,
                               byte[] data) {

  public static final int BINARY_HEADER_LENGTH = 26;

  public BinaryWithHeader(byte[] data) {
    this(
        BmvObjectUtils.bytesToEnum(Arrays.copyOfRange(data, 0, 1), BinaryType.class),
        BmvObjectUtils.bytesToInt(Arrays.copyOfRange(data, 1, 5)),
        BmvObjectUtils.bytesToBoolean(Arrays.copyOfRange(data, 5, 6)),
        BmvObjectUtils.bytesToUuid(Arrays.copyOfRange(data, 6, 22)),
        BmvObjectUtils.bytesToInt(Arrays.copyOfRange(data, 22, 26)),
        data
    );
  }

  public String getDatagramId() {
    return Datagram.buildDatagramId(binaryId.toString(), order);
  }

  public boolean isBinaryMessage() {
    return BinaryType.BINARY_MESSAGE == binaryType;
  }

  public boolean isMediaStream() {
    return BinaryType.MEDIA_STREAM == binaryType;
  }

  public byte[] getHeader() {
    return Arrays.copyOfRange(data, 0, BINARY_HEADER_LENGTH);
  }

  public byte[] getPayload() {
    return Arrays.copyOfRange(data, BINARY_HEADER_LENGTH, data.length);
  }
}
