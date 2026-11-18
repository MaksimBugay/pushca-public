package bmv.org.pushca.client.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BmvObjectUtilsTest {

  @SuppressWarnings("resource")
  @Test
  void scheduledTaskRunsAgainAfterAFailedRun() throws InterruptedException {
    AtomicInteger runs = new AtomicInteger();
    CountDownLatch ranAfterFailure = new CountDownLatch(1);
    ScheduledExecutorService scheduler = BmvObjectUtils.createScheduler(() -> {
      if (runs.incrementAndGet() == 1) {
        throw new IllegalStateException("Failed refresh token attempt");
      }
      ranAfterFailure.countDown();
    }, Duration.ofMillis(20), Duration.ZERO);
    try {
      assertTrue(ranAfterFailure.await(2, TimeUnit.SECONDS), "runs on after the first run failed");
    } finally {
      scheduler.shutdownNow();
    }
  }
}
