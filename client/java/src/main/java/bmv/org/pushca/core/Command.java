package bmv.org.pushca.core;

public enum Command {
  SEND_MESSAGE,
  SEND_MESSAGE_WITH_ACKNOWLEDGE,
  SEND_MESSAGE_WITH_PRESERVED_ORDER,
  SEND_ENVELOPES,
  SEND_BINARY_MANIFEST,
  PING,
  REFRESH_TOKEN,
  ACKNOWLEDGE,
  CREATE_CHANNEL;
}
