package bmv.org.pushca.client.model;

import java.util.List;

public class UploadBinaryAppeal {

  public PClient sender;
  public PClient owner;
  public String binaryId;
  public String binaryName;
  public int chunkSize;
  public boolean withAcknowledge;
  public List<Integer> requestedChunks;
}
