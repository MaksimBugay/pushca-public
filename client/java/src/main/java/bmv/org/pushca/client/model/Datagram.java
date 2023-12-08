package bmv.org.pushca.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Datagram {

  public int size;
  public String md5;
  public byte[] prefix;
  public String id;
  public int order;
  @JsonIgnore
  public byte[] preparedDataWithPrefix;
  @JsonIgnore
  private boolean received;

  public synchronized boolean isReceived() {
    return received;
  }

  public synchronized void setReceived(boolean received) {
    this.received = received;
  }
}
