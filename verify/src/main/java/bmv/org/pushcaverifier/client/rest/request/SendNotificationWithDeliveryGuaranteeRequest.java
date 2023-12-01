package bmv.org.pushcaverifier.client.rest.request;


import bmv.org.pushcaverifier.client.PClientWithPusherId;

public record SendNotificationWithDeliveryGuaranteeRequest(PClientWithPusherId client,
                                                           String message) {

}
