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

  public UploadBinaryAppeal() {
  }

  public UploadBinaryAppeal(PClient owner, String binaryId, String binaryName, int chunkSize) {
    this.owner = owner;
    this.binaryId = binaryId;
    this.binaryName = binaryName;
    this.chunkSize = chunkSize;
  }
}
