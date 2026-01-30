package bmv.pushca.binary.proxy.pushca.exception;

public class SendBinaryRuntimeError extends RuntimeException {

  public SendBinaryRuntimeError(SendBinaryError cause) {
    super(cause);
  }
}
