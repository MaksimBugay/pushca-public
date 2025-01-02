package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.api.request.GatewayRequestHeader;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import java.nio.charset.StandardCharsets;
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

  public WsGateway(IpGeoLookupService ipGeoLookupService) {
    this.ipGeoLookupService = ipGeoLookupService;
    this.gatewayPathProcessors = Map.of(
        Path.RESOLVE_IP_WITH_PROXY_CHECK.name(),
        this::resolveIpWithProxyCheck,
        Path.PING.name(),
        this::ping
    );
  }

  enum Path {RESOLVE_IP_WITH_PROXY_CHECK, PING}

  private final Map<String, BiFunction<GatewayRequestHeader, byte[], byte[]>> gatewayPathProcessors;

  public BiFunction<GatewayRequestHeader, byte[], byte[]> getPathProcessor(String path) {
    return gatewayPathProcessors.get(path);
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
}
