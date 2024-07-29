package bmv.pushca.binary.proxy.jms;

import bmv.pushca.binary.proxy.model.InternalMessage;
import java.util.concurrent.CompletableFuture;

public interface TopicProducer {

  CompletableFuture<Void> sendMessages(InternalMessage... messages);

  void close();
}
