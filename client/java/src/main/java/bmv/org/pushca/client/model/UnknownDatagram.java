package bmv.org.pushca.client.model;

import java.util.UUID;

public class UnknownDatagram {

  public final UUID binaryId;
  public final byte[] prefix;
  public final int order;

  public final byte[] data;

  public UnknownDatagram(UUID binaryId, byte[] prefix, int order, byte[] data) {
    this.binaryId = binaryId;
    this.prefix = prefix;
    this.order = order;
    this.data = data;
  }
}
