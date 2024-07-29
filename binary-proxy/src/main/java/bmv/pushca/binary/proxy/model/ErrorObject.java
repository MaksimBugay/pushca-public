package bmv.pushca.binary.proxy.model;

import java.util.concurrent.TimeoutException;

public record ErrorObject(int code, String message) {

  public static ErrorObject getInstance(Throwable throwable) {
    if (throwable instanceof TimeoutException) {
      return new ErrorObject(1, "No Response from server in defined timeout interval");
    }
    return new ErrorObject(0, throwable.getMessage());
  }

}
