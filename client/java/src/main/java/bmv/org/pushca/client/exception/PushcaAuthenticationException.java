package bmv.org.pushca.client.exception;

/**
 * Pushca refused to sign the client in (HTTP 401 or 403): wrong API key or password. Signing in again can't help,
 * so the client doesn't retry.
 */
public class PushcaAuthenticationException extends RuntimeException {

  private final int httpStatus;

  public PushcaAuthenticationException(String clientInfo, int httpStatus) {
    super("Pushca refused to sign in " + clientInfo + " (HTTP " + httpStatus + "): wrong API key or password");
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }
}
