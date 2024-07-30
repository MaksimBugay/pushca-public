package bmv.pushca.binary.proxy.pushca.model;

import java.util.List;

public record UploadBinaryAppeal(PClient sender, ClientSearchData owner,
                                 String binaryId,
                                 int chunkSize, boolean manifestOnly,
                                 List<Integer> requestedChunks) {

  public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;

  public UploadBinaryAppeal(PClient sender, ClientSearchData owner, String binaryId, int chunkSize,
      boolean manifestOnly) {
    this(sender, owner, binaryId, chunkSize, manifestOnly, null);
  }

  public UploadBinaryAppeal(PClient sender, ClientSearchData owner, String binaryId,
      boolean manifestOnly) {
    this(sender, owner, binaryId, DEFAULT_CHUNK_SIZE, manifestOnly, null);
  }
}
