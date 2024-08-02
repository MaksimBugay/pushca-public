package bmv.pushca.binary.proxy.pushca.exception;

import bmv.pushca.binary.proxy.pushca.model.Datagram;
import java.text.MessageFormat;

public class CannotDownloadBinaryChunkException extends RuntimeException {

  public final String binaryId;

  public final Datagram chunk;

  public final String downloadSessionId;

  public CannotDownloadBinaryChunkException(String binaryId, Datagram chunk,
      String downloadSessionId) {
    super(buildErrorMessage(binaryId, chunk.order()));
    this.binaryId = binaryId;
    this.chunk = chunk;
    this.downloadSessionId = downloadSessionId;
  }

  public CannotDownloadBinaryChunkException(String binaryId, Datagram chunk,
      String downloadSessionId, Throwable cause) {
    super(buildErrorMessage(binaryId, chunk.order()), cause);
    this.binaryId = binaryId;
    this.chunk = chunk;
    this.downloadSessionId = downloadSessionId;
  }

  private static String buildErrorMessage(String binaryId, int order) {
    return MessageFormat.format(
        "Impossible to download chunk {0} of binary with id {1}",
        String.valueOf(order),
        binaryId
    );
  }
}
