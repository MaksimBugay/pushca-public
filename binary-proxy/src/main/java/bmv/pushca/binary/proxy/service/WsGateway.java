package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.api.request.GatewayRequestHeader;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.pushca.model.Command;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WsGateway {

  private static final Logger LOGGER = LoggerFactory.getLogger(WsGateway.class);

  private final IpGeoLookupService ipGeoLookupService;

  private final WebsocketPool websocketPool;

  public WsGateway(IpGeoLookupService ipGeoLookupService, WebsocketPool websocketPool) {
    this.ipGeoLookupService = ipGeoLookupService;
    this.websocketPool = websocketPool;
    this.gatewayPathProcessors = Map.of(
        Path.RESOLVE_IP_WITH_PROXY_CHECK.name(),
        this::resolveIpWithProxyCheck,
        Path.PING.name(),
        this::ping
    );
    this.websocketPool.setGatewayRequestHandler(
        requestData -> websocketPool.runAsynchronously(() -> process(requestData))
            .whenComplete((result, throwable) -> {
              if (throwable != null) {
                LOGGER.warn("Failed process gateway request attempt: {}", requestData, throwable);
              }
            })
    );
  }

  enum Path {RESOLVE_IP_WITH_PROXY_CHECK, PING}

  private final Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayPathProcessors;

  public void process(GatewayRequestData requestData) {
    LOGGER.debug("Gateway request was received: {}", requestData);
    GatewayRequestHeader header = fromJson(requestData.header(), GatewayRequestHeader.class);
    byte[] requestPayload = new byte[0];
    if (requestData.base64RequestBody != null) {
      requestPayload = Base64.getDecoder().decode(requestData.base64RequestBody);
    }
    BiFunction<GatewayRequestHeader, byte[], byte[]> gatewayProcessor =
        gatewayPathProcessors.get(requestData.path);
    if (gatewayProcessor != null) {
      byte[] responsePayload = gatewayProcessor.apply(header, requestPayload);
      if (responsePayload == null) {
        responsePayload = new byte[0];
      }
      LOGGER.debug("Ready to send prepared gateway response");
      sendGatewayResponse(
          requestData.sequenceId,
          Base64.getEncoder().encodeToString(responsePayload)
      );
    }
  }

  private byte[] ping(GatewayRequestHeader header, byte[] ipAddress) {
    return "PONG".getBytes(StandardCharsets.UTF_8);
  }

  private byte[] resolveIpWithProxyCheck(GatewayRequestHeader header, byte[] ipAddress) {
    String ip;
    try {
      String requestJson = new String(ipAddress, StandardCharsets.UTF_8);
      ResolveIpRequest request = fromJson(requestJson, ResolveIpRequest.class);
      ip = Optional.ofNullable(request.ip()).orElse(header.ip);
    } catch (Exception ex) {
      LOGGER.warn("Broken resolve ip request", ex);
      return new byte[0];
    }

    GeoLookupResponse response = ipGeoLookupService.resolve(ip);

    return toJson(response).getBytes(StandardCharsets.UTF_8);
  }

  public void sendGatewayResponse(String id, String base64ResponseBody) {
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("id", id);
    metaData.put("payload", base64ResponseBody);
    websocketPool.sendCommand(id, Command.SEND_GATEWAY_RESPONSE, metaData);
  }

  public record GatewayRequestData(String sequenceId, String header, String path,
                                   String base64RequestBody) {

  }
}
