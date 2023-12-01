package bmv.org.pushcaverifier.client.rest.request;

import bmv.org.pushcaverifier.client.PClientWithPusherId;

public record SendNotificationRequest(String id,
                                      PClientWithPusherId filter,
                                      Boolean preserveOrder,
                                      String message){

  public SendNotificationRequest(PClientWithPusherId filter, String message) {
    this(null, filter, Boolean.FALSE, message);
  }

  public SendNotificationRequest(PClientWithPusherId filter, Boolean preserveOrder, String message) {
    this(null, filter, preserveOrder, message);
  }
}
