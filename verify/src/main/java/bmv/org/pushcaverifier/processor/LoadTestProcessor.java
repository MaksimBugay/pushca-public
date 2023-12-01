package bmv.org.pushcaverifier.processor;

import bmv.org.pushcaverifier.client.ClientWithPool;
import bmv.org.pushcaverifier.client.PClientWithPusherId;
import bmv.org.pushcaverifier.model.TestMessage;
import reactor.core.scheduler.Scheduler;

public interface LoadTestProcessor {

  void subscribe(PClientWithPusherId client, TestMessage message, ClientWithPool simpleClient, Scheduler workers,
      StatisticsHolder statisticsHolder);

  TestProcessorType getType();
}
