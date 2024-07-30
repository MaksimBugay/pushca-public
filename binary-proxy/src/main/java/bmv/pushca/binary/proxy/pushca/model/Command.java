package bmv.pushca.binary.proxy.pushca.model;

public enum Command {
  SEND_MESSAGE,
  SEND_MESSAGE_WITH_ACKNOWLEDGE,
  SEND_GATEWAY_REQUEST,
  SEND_GATEWAY_RESPONSE,
  SEND_MESSAGE_WITH_PRESERVED_ORDER,
  SEND_ENVELOPES,
  SEND_MESSAGE_TO_CHANNEL,
  SEND_BINARY_MANIFEST,
  SEND_UPLOAD_BINARY_APPEAL,
  PING,
  REFRESH_TOKEN,
  ACKNOWLEDGE,
  CREATE_CHANNEL,
  ADD_MEMBERS_TO_CHANNEL,
  MARK_CHANNEL_AS_READ,
  ADD_IMPRESSION,
  REMOVE_IMPRESSION,
  GET_IMPRESSION_STAT,
  GET_CHANNELS,
  GET_CHANNEL_HISTORY,
  GET_CHANNELS_PUBLIC_INFO,
  GET_CHANNEL_MEMBERS,
  REMOVE_ME_FROM_CHANNEL,
  REGISTER_FILTER,
  REMOVE_FILTER;
}
