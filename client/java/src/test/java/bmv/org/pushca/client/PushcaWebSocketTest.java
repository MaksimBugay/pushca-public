package bmv.org.pushca.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PushcaWebSocketTest {

  private static final long NOW = 1_794_984_057_000L;

  @Test
  void aTokenRefreshedOnScheduleIsReusedForReconnects() {
    assertFalse(PushcaWebSocket.isTokenOutdated(NOW - Duration.ofMinutes(1).toMillis(), NOW));
    // just before, or a bit after, its next refresh (every 10 min)
    assertFalse(PushcaWebSocket.isTokenOutdated(NOW - Duration.ofMinutes(11).toMillis(), NOW));
  }

  @Test
  void aTokenThatMissedItsRefreshesMeansSigningInAgain() {
    assertTrue(PushcaWebSocket.isTokenOutdated(NOW - Duration.ofMinutes(21).toMillis(), NOW));
    // the one in the log: issued about six hours earlier, expired after 30 min
    assertTrue(PushcaWebSocket.isTokenOutdated(NOW - Duration.ofHours(6).toMillis(), NOW));
  }
}
