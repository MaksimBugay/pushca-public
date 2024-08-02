package bmv.pushca.binary.proxy.pushca.exception;

import bmv.pushca.binary.proxy.pushca.model.Datagram;

public class CannotDownloadBinaryChunkException extends RuntimeException {

  public final String binaryId;

  public final Datagram chunk;

  public final String downloadSessionId;

  public CannotDownloadBinaryChunkException(String binaryId, Datagram chunk,
      String downloadSessionId) {
    this.binaryId = binaryId;
    this.chunk = chunk;
    this.downloadSessionId = downloadSessionId;
  }

  public CannotDownloadBinaryChunkException(Throwable cause, String binaryId, Datagram chunk,
      String downloadSessionId) {
    super(cause);
    this.binaryId = binaryId;
    this.chunk = chunk;
    this.downloadSessionId = downloadSessionId;
  }
}
