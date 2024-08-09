package bmv.pushca.binary.proxy.pushca.model;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;

import java.util.List;
import org.apache.commons.lang3.StringUtils;

public record BinaryManifest(String id, String name, String mimeType,
                             List<Datagram> datagrams, PClient sender,
                             String pusherInstanceId,
                             String downloadSessionId) {

  public BinaryManifest(String id, String name, String mimeType, List<Datagram> datagrams,
      PClient sender, String pusherInstanceId, String downloadSessionId) {
    this.id = id;
    this.name = name;
    this.mimeType = mimeType;
    this.datagrams = datagrams;
    this.sender = sender;
    this.pusherInstanceId = pusherInstanceId;
    this.downloadSessionId =
        StringUtils.isEmpty(downloadSessionId) ? ID_GENERATOR.generate().toString()
            : downloadSessionId;
  }

  public BinaryManifest(String id, String name, String mimeType, List<Datagram> datagrams,
      PClient sender, String pusherInstanceId) {
    this(id, name, mimeType, datagrams, sender, pusherInstanceId, null);
  }

  public long getTotalSize() {
    return datagrams.stream().map(Datagram::size).reduce(Integer::sum).orElse(0);
  }
}
