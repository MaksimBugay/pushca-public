package bmv.org.pushca.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class BinaryObjectMetadata {

  public String id;

  public String name;
  private List<Datagram> datagrams;
  public PClient sender;
  public String pusherInstanceId;

  public BinaryObjectMetadata() {
  }

  public BinaryObjectMetadata(String id, String name, List<Datagram> datagrams, PClient sender,
      String pusherInstanceId) {
    this.id = id;
    this.name = name;
    this.datagrams = datagrams;
    this.sender = sender;
    this.pusherInstanceId = pusherInstanceId;
  }

  @JsonIgnore
  public String getBinaryId() {
    if (StringUtils.isNotEmpty(id)) {
      return id;
    }
    return datagrams.stream().map(d -> d.id).findFirst()
        .orElseThrow(() -> new IllegalStateException("Binary id is not defined"));
  }

  @JsonIgnore
  public synchronized Datagram getDatagram(String id, int order) {
    return datagrams.stream()
        .filter(d -> d.id.equals(id) && (d.order == order))
        .findFirst().orElse(null);
  }

  public synchronized void markDatagramAsReceived(String id, int order) {
    getDatagram(id, order).received = true;
  }

  public synchronized List<Datagram> getDatagrams() {
    return datagrams;
  }

  public synchronized void setDatagrams(List<Datagram> datagrams) {
    this.datagrams = datagrams;
  }
}
