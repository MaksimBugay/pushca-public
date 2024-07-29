package bmv.pushca.binary.proxy.util.serialisation;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TaskRunner {

  private TaskRunner() {
  }

  public static void runWithDelay(Runnable task, long delayMs) {
    ScheduledExecutorService executor = null;
    try {
      executor = Executors.newSingleThreadScheduledExecutor();
      executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    } finally {
      Optional.ofNullable(executor).ifPresent(ExecutorService::shutdown);
    }

  }
}
