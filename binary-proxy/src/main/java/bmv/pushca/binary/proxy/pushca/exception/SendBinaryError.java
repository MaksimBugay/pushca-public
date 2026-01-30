package bmv.pushca.binary.proxy.pushca.exception;

public class SendBinaryError extends Exception {
  public SendBinaryError(String message) {
    super(message);
  }

  public SendBinaryError(String message, Throwable cause) {
    super(message, cause);
  }
}
