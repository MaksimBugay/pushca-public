package bmv.org.pushcaverifier.processor;

import static java.util.stream.Collectors.groupingBy;

import bmv.org.pushcaverifier.model.TestMessage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class StatisticsHolder {

  private final static Logger LOGGER = LoggerFactory.getLogger(StatisticsHolder.class);

  private static final String EXECUTION_RESULTS_KEY_NAME = "execution-results";

  private final ReactiveRedisOperations<String, ExecutionResults> redisOperations;

  private final Set<TestMessage> deliveredMessages = ConcurrentHashMap.newKeySet(500_000);

  private final Set<TestMessage> issuedMessages = ConcurrentHashMap.newKeySet(500_000);

  public StatisticsHolder(ReactiveRedisOperations<String, ExecutionResults> redisOperations) {
    this.redisOperations = redisOperations;
  }

  public void addDeliveredMessage(TestMessage message) {
    deliveredMessages.add(message);
  }

  public void addDeliveredMessages(Collection<TestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }
    deliveredMessages.addAll(messages);
  }

  public void addIssuedMessage(TestMessage message) {
    issuedMessages.add(message);
  }

  public Set<TestMessage> getIssuedMessages() {
    return issuedMessages;
  }

  public void printIterationStatistic(boolean withDistribution) {
    long total = issuedMessages.size();
    LOGGER.info("{} messages were successfully delivered", deliveredMessages.size());
    LOGGER.info("{} number of failed deliveries", total - deliveredMessages.size());

    List<Long> latencies = deliveredMessages.stream()
        .map(msg -> msg.deliveryTime() - msg.sendTime())
        .sorted()
        .toList();

    long percentile25 = percentile(latencies, 25);
    LOGGER.info("25th pct {} ms", percentile25);
    long percentile50 = percentile(latencies, 50);
    LOGGER.info("50th pct {} ms", percentile50);
    long percentile75 = percentile(latencies, 75);
    LOGGER.info("75th pct {} ms", percentile75);
    long percentile99 = percentile(latencies, 99);
    LOGGER.info("99th pct {} ms", percentile99);

    if (withDistribution) {
      Map<Integer, Integer> iDistribution =
          deliveredMessages.stream()
              .collect(groupingBy(TestMessage::iteration))
              .entrySet().stream()
              .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().size()));
      LOGGER.info("Distribution across iterations: {}", iDistribution);
    }

    redisOperations.opsForSet().add(EXECUTION_RESULTS_KEY_NAME,
        new ExecutionResults(
            total,
            deliveredMessages.size(),
            total - deliveredMessages.size(),
            percentile25,
            percentile50,
            percentile75,
            percentile99
        )).block();
  }

  public void resetStatistic() {
    redisOperations.delete(EXECUTION_RESULTS_KEY_NAME).block();
  }

  public void printStatistic() {
    List<ExecutionResults> results =
        redisOperations.opsForSet().members(EXECUTION_RESULTS_KEY_NAME).buffer().blockLast();

    if (CollectionUtils.isEmpty(results)) {
      LOGGER.warn("Execution data is absent");
      return;
    }

    LOGGER.info("Distributed execution statistic");
    long total = results.stream().map(v -> v.issued).reduce(Long::sum).orElse(0L);
    long delivered = results.stream().map(v -> v.delivered).reduce(Long::sum).orElse(0L);
    LOGGER.info("{} messages were successfully delivered", delivered);
    LOGGER.info("{} number of failed deliveries", total - delivered);

    long percentile25 =
        (long) Math.ceil(results.stream().mapToLong(v -> v.percentile25).average().orElse(0));
    LOGGER.info("25th pct {} ms", percentile25);
    long percentile50 =
        (long) Math.ceil(results.stream().mapToLong(v -> v.percentile50).average().orElse(0));
    LOGGER.info("50th pct {} ms", percentile50);
    long percentile75 =
        (long) Math.ceil(results.stream().mapToLong(v -> v.percentile75).average().orElse(0));
    LOGGER.info("75th pct {} ms", percentile75);
    long percentile99 =
        (long) Math.ceil(results.stream().mapToLong(v -> v.percentile99).average().orElse(0));
    LOGGER.info("99th pct {} ms", percentile99);
  }

  public void reset() {
    deliveredMessages.clear();
    issuedMessages.clear();
  }

  public static long percentile(List<Long> latencies, double percentile) {
    int index = (int) Math.ceil(percentile / 100.0 * latencies.size());
    return latencies.get(index - 1);
  }

  public record ExecutionResults(
      long issued,
      long delivered,
      long failed,
      long percentile25,
      long percentile50,
      long percentile75,
      long percentile99) {

  }

}
