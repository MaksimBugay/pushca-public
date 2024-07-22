package bmv.org.pushca.client.model;

import java.util.List;

public class UploadBinaryAppeal {

  public PClient sender;
  public ClientFilter owner;
  public String binaryId;
  public int chunkSize;
  public boolean manifestOnly;
  public List<Integer> requestedChunks;

  public UploadBinaryAppeal() {
  }

  public UploadBinaryAppeal(ClientFilter owner, String binaryId, int chunkSize) {
    this.owner = owner;
    this.binaryId = binaryId;
    this.chunkSize = chunkSize;
  }
}
