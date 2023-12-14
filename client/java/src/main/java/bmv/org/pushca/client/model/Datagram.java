package bmv.org.pushca.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Datagram {
  public int size;
  public String md5;
  public byte[] prefix;
  public int order;
  @JsonIgnore
  public byte[] data;
}
