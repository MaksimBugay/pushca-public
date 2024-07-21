package bmv.org.pushca.client.model;

import static bmv.org.pushca.client.utils.SendBinaryHelper.BINARY_HEADER_LENGTH;

import bmv.org.pushca.client.utils.BmvObjectUtils;
import bmv.org.pushca.client.utils.SendBinaryHelper.BinaryType;
import java.util.Arrays;
import java.util.UUID;

public class BinaryWithHeader {

  public final BinaryType binaryType;
  public final int clientHash;
  public final boolean withAcknowledge;
  public final UUID binaryId;
  public final int order;
  public final byte[] data;

  public BinaryWithHeader(BinaryType binaryType, int clientHash, boolean withAcknowledge,
      UUID binaryId, int order, byte[] data) {
    this.binaryType = binaryType;
    this.clientHash = clientHash;
    this.withAcknowledge = withAcknowledge;
    this.binaryId = binaryId;
    this.order = order;
    this.data = data;
  }

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
