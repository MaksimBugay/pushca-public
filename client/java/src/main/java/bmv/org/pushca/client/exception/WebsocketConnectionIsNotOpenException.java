package bmv.org.pushca.client.exception;

/**
 * A command was sent while the web socket connection is not open: not signed in yet, or broken and not reconnected
 * yet. The client reconnects by itself (keep-alive), so a later attempt may succeed.
 */
public class WebsocketConnectionIsNotOpenException extends IllegalStateException {

  public WebsocketConnectionIsNotOpenException() {
    super("Web socket connection is broken");
  }
}
