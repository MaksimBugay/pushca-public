package bmv.org.pushcaverifier;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BloomFilterTest {
  @Test
  void profileWasAlreadyLaunchedTest() {
    BloomFilter<String> alreadyLaunchedFilter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        10_000_000, 0.005
    );
    for (int i = 0; i < 100_000; i++) {
      String profileId = UUID.randomUUID().toString();
      for (int n = 0; n < 1000; n++) {
        Assertions.assertFalse(alreadyLaunchedFilter.mightContain(profileId));
      }
      alreadyLaunchedFilter.put(profileId);
      for (int n = 0; n < 1000; n++) {
        Assertions.assertTrue(alreadyLaunchedFilter.mightContain(profileId));
      }
    }
  }
}
