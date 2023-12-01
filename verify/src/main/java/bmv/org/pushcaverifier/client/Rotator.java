package bmv.org.pushcaverifier.client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Rotator {

  private final AtomicInteger counter = new AtomicInteger();

  private final Supplier<Integer> boundProvider;

  public Rotator(Supplier<Integer> boundProvider) {
    this.boundProvider = boundProvider;
  }

  public Rotator(int bound) {
    this(() -> bound);
  }

  public int getNext() {
    return counter.updateAndGet(x -> (x + 1) < boundProvider.get() ? x + 1 : 0);
  }
}
