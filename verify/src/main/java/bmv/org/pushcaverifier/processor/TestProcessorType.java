package bmv.org.pushcaverifier.processor;

import bmv.org.pushcaverifier.client.Channel;

public enum TestProcessorType {
  SIMPLE_MESSAGE_REST(Channel.http, false),
  SIMPLE_MESSAGE_WS(Channel.ws, false),
  BINARY_MESSAGE_WS(Channel.ws, false),
  BINARY_MESSAGE_WITH_ACKNOWLEDGE_WS(Channel.ws, true),
  MESSAGE_WITH_ACKNOWLEDGE_REST(Channel.http, true),
  MESSAGE_WITH_ACKNOWLEDGE_WS(Channel.ws, true),

  MESSAGE_WITH_DELIVERY_GUARANTEE_REST(Channel.http, false);
  private final Channel channel;

  private final boolean withAcknowledgeCheck;

  TestProcessorType(Channel channel, boolean withAcknowledgeCheck) {
    this.channel = channel;
    this.withAcknowledgeCheck = withAcknowledgeCheck;
  }

  public Channel getChannel() {
    return channel;
  }

  public boolean isWithAcknowledgeCheck() {
    return withAcknowledgeCheck;
  }
}
