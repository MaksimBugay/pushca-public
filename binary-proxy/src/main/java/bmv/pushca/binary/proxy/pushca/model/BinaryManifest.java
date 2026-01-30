package bmv.pushca.binary.proxy.pushca.model;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public record BinaryManifest(String id, String name, String mimeType,
                             String readMeText,
                             List<Datagram> datagrams,
                             String senderIP,
                             PClient sender,
                             String pusherInstanceId,
                             String downloadSessionId,
                             Boolean forHuman,
                             Long expireAt) {

  public BinaryManifest(String id, String name, String mimeType, String readMeText,
                        List<Datagram> datagrams, String senderIP, PClient sender, String pusherInstanceId,
                        String downloadSessionId, Boolean forHuman, Long expireAt) {
    this.id = id;
    this.name = name;
    this.mimeType = mimeType;
    this.readMeText = readMeText;
    this.datagrams = datagrams;
    this.senderIP = senderIP;
    this.sender = sender;
    this.pusherInstanceId = pusherInstanceId;
    this.downloadSessionId =
        StringUtils.isEmpty(downloadSessionId) ? ID_GENERATOR.generate().toString()
            : downloadSessionId;
    this.forHuman = forHuman;
    this.expireAt = expireAt;
  }

  public BinaryManifest(String id, String name, String mimeType, String readMeText,
                        List<Datagram> datagrams,
                        PClient sender, String pusherInstanceId, String downloadSessionId) {
    this(id, name, mimeType, readMeText, datagrams, null, sender, pusherInstanceId,
        downloadSessionId, Boolean.FALSE, null);
  }

  public BinaryManifest(String id, String name, String mimeType, String readMeText,
                        List<Datagram> datagrams, PClient sender, String pusherInstanceId) {
    this(id, name, mimeType, readMeText, datagrams, sender, pusherInstanceId, null);
  }

  public long getTotalSize() {
    long totalSize = 0;
    for (Datagram datagram : this.datagrams) {
      totalSize += datagram.size();
    }
    return totalSize;
  }
}
