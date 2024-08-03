package bmv.pushca.binary.proxy.pushca.model;

import bmv.pushca.binary.proxy.pushca.exception.InvalidWaitingResponseException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResponseWaiter<T> extends CompletableFuture<T> {

  private final long creationTime = Instant.now().toEpochMilli();

  private final AtomicLong activationTime = new AtomicLong(0);
  private final Function<T, Boolean> validator;
  private final Consumer<T> successHandler;
  private final Consumer<Throwable> errorHandler;
  private final String errorMessage;
  private final Runnable repeatAction;
  public final long[] intervals;
  public final long maxTtl;

  public ResponseWaiter(Function<T, Boolean> validator, Consumer<T> successHandler,
      Consumer<Throwable> errorHandler, String errorMessage,
      Runnable repeatAction, long repeatInterval, long maxTtl) {
    this.validator = validator;
    this.successHandler = successHandler;
    this.errorHandler = errorHandler;
    this.errorMessage = errorMessage;
    this.repeatAction = repeatAction;
    this.intervals = new long[] {repeatInterval, 2 * repeatInterval, 3 * repeatInterval};
    this.maxTtl = maxTtl;
  }

  public ResponseWaiter(long repeatInterval) {
    this.validator = null;
    this.successHandler = null;
    this.errorHandler = null;
    this.errorMessage = null;
    this.repeatAction = null;
    activate();
    this.intervals = new long[] {repeatInterval, 2 * repeatInterval, 3 * repeatInterval};
    this.maxTtl = 3 * repeatInterval;
  }

  public boolean isNotActivated() {
    return activationTime.get() == 0;
  }

  public boolean isExpired() {
    if (isNotActivated()) {
      return (Instant.now().toEpochMilli() - creationTime) > maxTtl;
    }
    return (Instant.now().toEpochMilli() - activationTime.get()) > intervals[2];
  }

  public void activate() {
    this.activationTime.set(Instant.now().toEpochMilli());
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
      Optional.ofNullable(errorHandler)
          .ifPresent(h -> h.accept(new InvalidWaitingResponseException(errorMessage, ex)));
    }
    return false;
  }

  public void runRepeatAction() {
    if (repeatAction == null) {
      return;
    }
    if (this.isDone()) {
      return;
    }
    if (isNotActivated()) {
      return;
    }
    long delta = Instant.now().toEpochMilli() - activationTime.get();
    if (delta >= intervals[0] && delta < intervals[1]) {
      repeatAction.run();
    } else if (delta >= intervals[1] && delta < intervals[2]) {
      repeatAction.run();
    }
  }
}
