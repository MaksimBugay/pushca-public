package bmv.pushca.binary.proxy.pushca.model;

import bmv.pushca.binary.proxy.pushca.exception.InvalidWaitingResponseException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResponseWaiter<T> extends CompletableFuture<T> {

  private final Function<T, Boolean> validator;

  private final Consumer<T> successHandler;
  private final Consumer<Throwable> errorHandler;
  private final String errorMessage;

  private final AtomicInteger errorCounter = new AtomicInteger();

  public ResponseWaiter(Function<T, Boolean> validator, Consumer<T> successHandler,
      Consumer<Throwable> errorHandler, String errorMessage) {
    this.validator = validator;
    this.successHandler = successHandler;
    this.errorHandler = errorHandler;
    this.errorMessage = errorMessage;
  }

  public ResponseWaiter() {
    this.validator = null;
    this.successHandler = null;
    this.errorHandler = null;
    this.errorMessage = null;
  }

  public boolean isResponseValid(T responseObj) {
    if (validator == null) {
      return true;
    }
    try {
      if (!validator.apply(responseObj)) {
        throw new IllegalArgumentException();
      }
      Optional.ofNullable(successHandler).ifPresent(handler -> handler.accept(responseObj));
      return true;
    } catch (Exception ex) {
      if (errorCounter.incrementAndGet() < 3) {
        Optional.ofNullable(errorHandler)
            .ifPresent(h -> h.accept(new InvalidWaitingResponseException(errorMessage, ex)));
      }
    }
    return false;
  }
}
