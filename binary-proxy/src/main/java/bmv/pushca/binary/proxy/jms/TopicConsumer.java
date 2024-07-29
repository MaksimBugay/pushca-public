package bmv.pushca.binary.proxy.jms;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.model.InternalErrorMessage;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsRoute;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsStep;
import bmv.pushca.binary.proxy.module.PayloadProcessorModule;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;

public abstract class TopicConsumer<T, P extends PayloadProcessorModule<T>> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TopicConsumer.class.getName());

  protected final String jmsTopicName;
  protected final P payloadProcessor;
  private final Class<T> payloadClass;
  private final TopicProducer topicProducer;

  public TopicConsumer(String jmsTopicName, Class<T> payloadClass,
      P payloadProcessor, TopicProducer topicProducer) {
    this.jmsTopicName = jmsTopicName;
    this.payloadClass = payloadClass;
    this.payloadProcessor = payloadProcessor;
    this.topicProducer = topicProducer;
  }

  public abstract Disposable consumeMessages();

  protected void processInternalMessage(InternalMessage internalMessage) {
    T payload;
    try {
      DataForProcessing<T> dataForProcessing = validateAndFetchDataForProcessing(internalMessage);
      payload = dataForProcessing.operation.apply(internalMessage.id, dataForProcessing.payload);
      routeProcessingResult(new ProcessingResult<>(internalMessage, payload));
    } catch (Exception ex) {
      LOGGER.error(
          "Error during attempt to process internal message {}", toJson(internalMessage), ex);
      routeError(internalMessage, ex);
    }
  }

  private void routeError(InternalMessage internalMessage, Exception ex) {
    JmsRoute responseRoute = internalMessage.getResponseRoute();
    if (responseRoute != null) {
      topicProducer.sendMessages(new InternalErrorMessage(internalMessage.id, ex, responseRoute));
    }
  }

  private DataForProcessing<T> validateAndFetchDataForProcessing(InternalMessage internalMessage) {
    if (!Object.class.equals(payloadClass) && !payloadClass.getSimpleName()
        .equals(internalMessage.payloadClassName)) {
      throw new IllegalArgumentException(String
          .format("Wrong payload type %s, should be %s", internalMessage.payloadClassName,
              payloadClass.getSimpleName()));
    }
    T payload;
    try {
      payload = fromJson(internalMessage.payload, payloadClass);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Cannot deserialize payload, " + internalMessage.payload);
    }
    if (CollectionUtils.isEmpty(internalMessage.steps)) {
      throw new IllegalArgumentException("Internal message should contains steps");
    }
    JmsRoute currentJmsRoute = Arrays.stream(internalMessage.steps.get(0).routes())
        .filter(jmsRoute -> jmsTopicName.equals(jmsRoute.topicName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Internal message with malformed routs"));
    BiFunction<UUID, T, T> operation = payloadProcessor.supportedOperations().get(currentJmsRoute.operation());
    if (operation == null) {
      throw new IllegalArgumentException(
          "Internal message contains unsupported operation " + currentJmsRoute.operation());
    }
    return new DataForProcessing<>(payload, operation);
  }

  private void routeProcessingResult(ProcessingResult<T> processingResult) {
    List<JmsStep> steps = new LinkedList<>(processingResult.originalInternalMessage.steps);
    steps.remove(0);
    if (steps.size() == 0) {
      return;
    }
    InternalMessage processedMessage = processingResult.originalInternalMessage
        .cloneWithPayloadAndSteps(toJson(processingResult.payload), steps);
    topicProducer.sendMessages(processedMessage);
  }

  private record DataForProcessing<T>(T payload, BiFunction<UUID, T, T> operation) {

  }

  private record ProcessingResult<T>(InternalMessage originalInternalMessage, T payload) {

  }
}
