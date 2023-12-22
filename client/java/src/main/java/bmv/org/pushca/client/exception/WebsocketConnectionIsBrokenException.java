package bmv.org.pushca.client.exception;

public class WebsocketConnectionIsBrokenException extends RuntimeException {

  public WebsocketConnectionIsBrokenException(Throwable cause) {
    super("Impossible to complete operation", cause);
  }
}
