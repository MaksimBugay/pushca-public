package bmv.pushca.binary.proxy.pushca;

import bmv.pushca.binary.proxy.pushca.exception.SendBinaryError;
import reactor.core.publisher.Mono;

public interface SendBinaryAgent {

  void send(byte[] data) throws SendBinaryError;

  /**
   * Sends binary data asynchronously and returns a Mono that completes when
   * the data has been written to the WebSocket channel.
   *
   * @param data the binary data to send
   * @return Mono that completes when the data is sent, or errors on failure
   */
  Mono<Void> sendAsync(byte[] data);

  default void send(byte[] data, boolean reThrowException) {
    try {
      send(data);
    } catch (SendBinaryError e) {
      if (reThrowException) {
        throw new RuntimeException(e);
      }
    }
  }
}
