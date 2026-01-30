package bmv.pushca.binary.proxy.pushca.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.text.MessageFormat;

public record Datagram(
    int size,
    String md5,
    String prefix,
    int order,
    @JsonIgnore
    byte[] data
) {

  public static String buildDatagramId(String binaryId, int order, int destHashCode) {
    return MessageFormat.format("{0}-{1}-{2}", binaryId, String.valueOf(order),
        String.valueOf(destHashCode));
  }
}
