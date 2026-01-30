package bmv.pushca.binary.proxy.pushca;

import bmv.pushca.binary.proxy.pushca.exception.SendBinaryError;

public interface SendBinaryAgent {

  void send(byte[] data) throws SendBinaryError;

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
