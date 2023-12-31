package bmv.org.pushcaverifier.client;

import static bmv.org.pushcaverifier.util.BmvObjectUtils.booleanToBytes;
import static bmv.org.pushcaverifier.util.BmvObjectUtils.intToBytes;
import static bmv.org.pushcaverifier.util.BmvObjectUtils.uuidToBytes;
import static bmv.org.pushcaverifier.util.JsonUtility.toJson;
import static org.apache.commons.lang3.ArrayUtils.addAll;

import bmv.org.pushcaverifier.client.rest.request.SendNotificationRequest;
import bmv.org.pushcaverifier.client.rest.request.SendNotificationWithAcknowledgeRequest;
import bmv.org.pushcaverifier.client.rest.request.SendNotificationWithDeliveryGuaranteeRequest;
import bmv.org.pushcaverifier.config.ConfigService;
import bmv.org.pushcaverifier.model.TestMessage;
import bmv.org.pushcaverifier.pushca.Command;
import bmv.org.pushcaverifier.pushca.PushcaMessageFactoryService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MessageService {

  public static final Logger LOGGER = LoggerFactory.getLogger(MessageService.class);

  private final String sendMessageUrl;

  private final String sendMessageWithAcknowledgeUrl;

  private final String sendMessageWithDeliveryGuaranteeUrl;

  @Autowired
  public MessageService(ConfigService configService) {
    this.sendMessageUrl = configService.getPushcaCoordinatorUrl() + "/send-notification";
    this.sendMessageWithAcknowledgeUrl =
        configService.getPushcaCoordinatorUrl() + "/send-notification-with-acknowledge";
    this.sendMessageWithDeliveryGuaranteeUrl =
        configService.getPushcaCoordinatorUrl() + "/send-notification-with-delivery-guarantee";
  }

  public void sendBinary(ClientWithPool clientWithPool, TestMessage message) {
    int clientHashCode = message.receiver().getClient().hashCode();
    byte[] prefix = addAll(intToBytes(clientHashCode), booleanToBytes(false));
    prefix = addAll(prefix, uuidToBytes(UUID.fromString(message.id())));
    prefix = addAll(prefix, intToBytes(Integer.MAX_VALUE));
    clientWithPool.send(addAll(prefix, Base64.getEncoder().encode(toJson(message).getBytes(
        StandardCharsets.UTF_8))));
  }

  public void sendBinaryWithAcknowledge(ClientWithPool clientWithPool, TestMessage message) {
    int clientHashCode = message.receiver().getClient().hashCode();
    byte[] prefix = addAll(intToBytes(clientHashCode), booleanToBytes(true));
    prefix = addAll(prefix, uuidToBytes(UUID.fromString(message.id())));
    prefix = addAll(prefix, intToBytes(Integer.MAX_VALUE));
    clientWithPool.send(addAll(prefix, Base64.getEncoder().encode(toJson(message).getBytes(
        StandardCharsets.UTF_8))));
  }


  public void sendMessageWithAcknowledge(ClientWithPool clientWithPool, TestMessage message) {
    clientWithPool.send(PushcaMessageFactoryService.buildCommandMessage(
        Command.SEND_MESSAGE_WITH_ACKNOWLEDGE,
        Map.of(
            "id", message.id(),
            "client", message.receiver(),
            "message", toJson(message),
            "preserveOrder", message.preserveOrder())
    ).command());
  }

  public Mono<Void> sendMessageWithAcknowledge(WebClient dedicatedWebClient, TestMessage message) {
    final SendNotificationWithAcknowledgeRequest request =
        new SendNotificationWithAcknowledgeRequest(message.receiver(), message.preserveOrder(),
            toJson(message));
    return dedicatedWebClient.post()
        .uri(sendMessageWithAcknowledgeUrl)
        .body(Mono.just(request), SendNotificationWithAcknowledgeRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(clientResponse -> {
          if (!HttpStatus.OK.equals(clientResponse.statusCode())) {
            LOGGER.error("Failed attempt to send message {}", message);
            throw new IllegalStateException("Failed attempt to send message " + message);
          } else {
            LOGGER.debug("Message was successfully sent, {}", message);
            return Mono.empty();
          }
        });
  }

  public Mono<Void> sendMessageWithDeliveryGuarantee(WebClient dedicatedWebClient,
      TestMessage message) {
    final SendNotificationWithDeliveryGuaranteeRequest request =
        new SendNotificationWithDeliveryGuaranteeRequest(message.receiver(), toJson(message));
    return dedicatedWebClient.post()
        .uri(sendMessageWithDeliveryGuaranteeUrl)
        .body(Mono.just(request), SendNotificationWithDeliveryGuaranteeRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(clientResponse -> {
          if (!HttpStatus.OK.equals(clientResponse.statusCode())) {
            LOGGER.error("Failed attempt to send message {}", message);
            throw new IllegalStateException("Failed attempt to send message " + message);
          } else {
            LOGGER.debug("Message was successfully sent, {}", message);
            return Mono.empty();
          }
        });
  }

  public void sendMessage(ClientWithPool sender, TestMessage message) {
    sender.send(PushcaMessageFactoryService.buildCommandMessage(
        Command.SEND_MESSAGE,
        Map.of("filter", message.receiver(), "message", toJson(message), "preserveOrder",
                message.preserveOrder())
    ).command());
  }

  public Mono<Void> sendMessage(WebClient dedicatedWebClient, TestMessage message) {
    final SendNotificationRequest request =
        new SendNotificationRequest(message.receiver(), message.preserveOrder(), toJson(message));
    return dedicatedWebClient.post()
        .uri(sendMessageUrl)
        .body(Mono.just(request), SendNotificationRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(clientResponse -> {
          if (!HttpStatus.OK.equals(clientResponse.statusCode())) {
            LOGGER.error("Failed attempt to send message {}", message);
            throw new IllegalStateException("Failed attempt to send message " + message);
          } else {
            LOGGER.debug("Message was successfully sent, {}", message);
            return Mono.empty();
          }
        });
  }
}
