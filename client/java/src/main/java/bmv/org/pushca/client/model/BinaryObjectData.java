package bmv.org.pushca.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Optional;

public class BinaryObjectData {

  public String id;

  public String name;
  private List<Datagram> datagrams;
  public PClient sender;
  public String pusherInstanceId;
  public boolean redOnly;
  public long created = System.currentTimeMillis();

  public BinaryObjectData() {
  }

  public BinaryObjectData(String id, String name, List<Datagram> datagrams, PClient sender,
      String pusherInstanceId) {
    this.id = id;
    this.name = name;
    this.datagrams = datagrams;
    this.sender = sender;
    this.pusherInstanceId = pusherInstanceId;
  }

  public String getBinaryId() {
    return id;
  }

  @JsonIgnore
  public synchronized Datagram getDatagram(int order) {
    return datagrams.stream()
        .filter(d -> d.order == order)
        .findFirst().orElse(null);
  }

  public synchronized void fillWithReceivedData(int order, byte[] data) {
    Optional.ofNullable(getDatagram(order))
        .ifPresent(datagram -> datagram.data = data);
  }

  public synchronized List<Datagram> getDatagrams() {
    return datagrams;
  }

  public synchronized void setDatagrams(List<Datagram> datagrams) {
    this.datagrams = datagrams;
  }

  @JsonIgnore
  public boolean isCompleted() {
    return getDatagrams().stream().allMatch(d -> d.data != null);
  }
}
