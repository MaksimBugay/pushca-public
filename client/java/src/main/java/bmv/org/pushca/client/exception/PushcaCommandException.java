package bmv.org.pushca.client.exception;

/**
 * Pushca server rejected a command, {@link #getError()} holds the error reported by the server
 */
public class PushcaCommandException extends IllegalStateException {

  private final String error;

  public PushcaCommandException(String message, String error) {
    super(error == null ? message : message + ": " + error);
    this.error = error;
  }

  public String getError() {
    return error;
  }
}
