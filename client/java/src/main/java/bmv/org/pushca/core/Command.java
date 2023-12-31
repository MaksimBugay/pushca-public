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
  CREATE_CHANNEL,
  ADD_MEMBERS_TO_CHANNEL,
  GET_CHANNELS,
  MARK_CHANNEL_AS_READ,
  SEND_MESSAGE_TO_CHANNEL,
  REMOVE_ME_FROM_CHANNEL,
  REGISTER_FILTER,
  REMOVE_FILTER;
}
