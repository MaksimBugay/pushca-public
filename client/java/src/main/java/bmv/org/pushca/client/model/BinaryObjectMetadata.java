package bmv.org.pushca.client.model;

import java.util.List;

public class BinaryObjectMetadata {
  public String name;
  public List<Datagram> datagrams;
  public PClient sender;
  public String pusherInstanceId;

  public BinaryObjectMetadata() {
  }

  public BinaryObjectMetadata(String name, List<Datagram> datagrams, PClient sender,
      String pusherInstanceId) {
    this.name = name;
    this.datagrams = datagrams;
    this.sender = sender;
    this.pusherInstanceId = pusherInstanceId;
  }
}
