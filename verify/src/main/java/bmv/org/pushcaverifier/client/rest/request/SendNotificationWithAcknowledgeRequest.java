package bmv.org.pushcaverifier.client.rest.request;


import bmv.org.pushcaverifier.client.PClientWithPusherId;

public record SendNotificationWithAcknowledgeRequest(String id,
                                                     PClientWithPusherId client,
                                                     Boolean preserveOrder,
                                                     String message) {

  public SendNotificationWithAcknowledgeRequest(PClientWithPusherId client, String message) {
    this(null, client, Boolean.FALSE, message);
  }

  public SendNotificationWithAcknowledgeRequest(PClientWithPusherId client, Boolean preserveOrder,
      String message) {
    this(null, client, preserveOrder, message);
  }
}

