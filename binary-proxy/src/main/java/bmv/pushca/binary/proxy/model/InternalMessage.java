package bmv.pushca.binary.proxy.model;


import bmv.pushca.binary.proxy.internal.Operation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

public class InternalMessage {

  public final UUID id;

  public final Type type;

  public final String payload;

  public final String payloadClassName;

  public final List<JmsStep> steps;

  @JsonCreator
  public InternalMessage(@JsonProperty("id") UUID id,
      @JsonProperty("type") Type type,
      @JsonProperty("payload") String payload,
      @JsonProperty("payloadClassName") String payloadClassName,
      @JsonProperty("steps") List<JmsStep> steps) {
    this.id = id;
    this.type = type;
    this.payload = payload;
    this.payloadClassName = payloadClassName;
    this.steps = (steps == null) ? null : new LinkedList<>(steps);
  }

  public InternalMessage cloneWithPayloadAndSteps(String payload, List<JmsStep> steps) {
    return new InternalMessage(
        this.id,
        type, payload,
        this.payloadClassName,
        steps
    );
  }

  @JsonIgnore
  public List<SendData> getSendData() {
    if (CollectionUtils.isEmpty(steps)) {
      throw new IllegalArgumentException("Internal message should contains steps");
    }
    return Arrays.stream(steps.get(0).routes)
        .map(jmsRoute -> new SendData(this, jmsRoute.topicName()))
        .collect(Collectors.toList());
  }

  @JsonIgnore
  public JmsRoute getResponseRoute() {
    return steps.stream().flatMap(step -> Arrays.stream(step.routes))
        .filter(jmsRoute -> jmsRoute.topicName.startsWith(JmsTopic.RESPONSE.getTopicName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InternalMessage that)) {
      return false;
    }
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public enum Type {COMMAND, QUERY, ERROR}

  public record JmsRoute(String topicName, Operation operation) {

  }

  public record JmsStep(JmsRoute[] routes) {

  }

  public record SendData(InternalMessage internalMessage, String topicName) {

  }
}
