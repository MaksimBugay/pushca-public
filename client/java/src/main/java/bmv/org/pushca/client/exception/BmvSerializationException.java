package bmv.org.pushca.client.exception;

public class BmvSerializationException extends RuntimeException {
  public BmvSerializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public BmvSerializationException(Throwable cause) {
    super(cause);
  }
}
