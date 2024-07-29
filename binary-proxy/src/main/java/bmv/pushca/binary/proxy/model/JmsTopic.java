package bmv.pushca.binary.proxy.model;

public enum JmsTopic {
  RESPONSE("jms.api-gateway.response"),
  PROFILE("jms.api-gateway.profile"),
  MONITOR("jms.api-gateway.monitor");

  private final String topicName;

  JmsTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
