package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJson;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;

import bmv.pushca.binary.proxy.api.request.GatewayRequestHeader;
import bmv.pushca.binary.proxy.api.request.PublishRemoteStreamRequest;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.api.response.PublishRemoteStreamResponse;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.PushcaRateLimitService;
import bmv.pushca.binary.proxy.pushca.model.Command;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import bmv.pushca.binary.proxy.pushca.model.GatewayRequestor;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.pushca.model.WsGatewayRateLimitCheckData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class WsGateway implements RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WsGateway.class);

    private final IpGeoLookupService ipGeoLookupService;

    private final WebsocketPool websocketPool;

    private final PushcaConfig pushcaConfig;

    private final PublishBinaryService publishBinaryService;

    private final RateLimitService rateLimitService;

    private final PClient gatewayHostPushcaClient;

    public WsGateway(IpGeoLookupService ipGeoLookupService,
                     WebsocketPool websocketPool,
                     PushcaConfig pushcaConfig,
                     PublishBinaryService publishBinaryService) {
        this.ipGeoLookupService = ipGeoLookupService;
        this.websocketPool = websocketPool;
        this.pushcaConfig = pushcaConfig;
        this.publishBinaryService = publishBinaryService;
        this.gatewayPathProcessors = Map.of(
                Path.RESOLVE_IP_WITH_PROXY_CHECK.name(),
                this::resolveIpWithProxyCheckAsync,
                Path.PING.name(),
                this::pingAsync,
                Path.PUBLISH_REMOTE_STREAM.name(),
                this::publishRemoteStream
        );
        this.websocketPool.setGatewayRequestHandler(
                requestData -> process(requestData)
                        .doOnError(
                                error -> LOGGER.warn("Failed process gateway request attempt: {}", requestData, error)
                        )
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe()
        );
        this.rateLimitService = new PushcaRateLimitService(
                this.publishBinaryService.getWebClient(),
                pushcaConfig.getPushcaClusterUrl(),
                pushcaConfig.isPushcaGatewayRateLimitEnabled()
        );
        this.gatewayHostPushcaClient = websocketPool.getHostPushcaClient();
    }

    @Override
    public Mono<Boolean> isAllowed(Object rateLimitKey) {
        return Mono.fromCallable(
                        () -> rateLimitService
                )
                .flatMap(
                        rateLimitService -> rateLimitService.isAllowed(rateLimitKey)
                )
                .switchIfEmpty(
                        Mono.just(Boolean.TRUE)
                );
    }

    public enum Path {RESOLVE_IP_WITH_PROXY_CHECK, PING, PUBLISH_REMOTE_STREAM}

    private final Map<String, BiFunction<GatewayRequestHeader, byte[], Mono<byte[]>>> gatewayPathProcessors;

    public Mono<Void> process(GatewayRequestData requestData) {
        LOGGER.info("Gateway request was received: {}", requestData);
        GatewayRequestHeader header = fromJson(requestData.header(), GatewayRequestHeader.class);
        byte[] requestPayload;
        if (requestData.base64RequestBody != null) {
            requestPayload = Base64.getDecoder().decode(requestData.base64RequestBody);
        } else {
            requestPayload = new byte[0];
        }

        return isAllowed(
                new WsGatewayRateLimitCheckData(
                        gatewayHostPushcaClient,
                        requestData.path,
                        new GatewayRequestor(header.client, header.ip),
                        null
                )
        ).flatMap(
                        rtCheckResult -> {
                            if (rtCheckResult) {
                                return Mono.justOrEmpty(
                                        gatewayPathProcessors.get(requestData.path)
                                );
                            }
                            return Mono.error(
                                    new RuntimeException("Rate limit exceeded")
                            );
                        }
                )
                .flatMap(
                        operation -> operation.apply(header, requestPayload)
                )
                .doOnSuccess(
                        responsePayload -> {
                            byte[] responseForSending = (responsePayload == null) ? new byte[0] : responsePayload;
                            LOGGER.debug("Ready to send prepared gateway response");
                            sendGatewayResponse(
                                    requestData.sequenceId,
                                    Base64.getEncoder().encodeToString(responseForSending)
                            );
                        }
                )
                .switchIfEmpty(
                        Mono.error(
                                new RuntimeException("Unknow gateway path: " + requestData.path)
                        )
                )
                .then();
    }

    private Mono<byte[]> pingAsync(GatewayRequestHeader header, byte[] ipAddress) {
        return Mono.just(ping());
    }

    private byte[] ping() {
        return "PONG".getBytes(StandardCharsets.UTF_8);
    }

    private Mono<byte[]> publishRemoteStream(GatewayRequestHeader header, byte[] requestBytes) {
        return Mono.fromCallable(
                        () -> extractPublishRemoteStreamRequest(requestBytes)
                )
                .flatMap(
                        request -> publishBinaryService.publishRemoteStream(
                                pushcaConfig.getPublishRemoteStreamServicePath(),
                                request.url(),
                                request.forHuman(),
                                request.expiredAt(),
                                0,
                                null
                        )
                )
                .doOnNext(
                        publicUrl -> LOGGER.info(
                                "Remote stream {} was successfully published to {}",
                                extractPublishRemoteStreamRequest(requestBytes),
                                publicUrl
                        )
                )
                .map(
                        publicUrl -> {
                            PublishRemoteStreamResponse response = new PublishRemoteStreamResponse(publicUrl, "");

                            return toJson(response).getBytes(StandardCharsets.UTF_8);
                        }
                )
                .doOnError(
                        error -> LOGGER.warn("Unexpected error during publish remote stream attempt", error)
                )
                .onErrorResume(
                        error -> Mono.just(
                                toJson(
                                        new PublishRemoteStreamResponse("", error.getMessage())
                                ).getBytes(StandardCharsets.UTF_8)
                        )
                );
    }

    private PublishRemoteStreamRequest extractPublishRemoteStreamRequest(byte[] requestBytes) {
        String requestJson = new String(requestBytes, StandardCharsets.UTF_8);
        return fromJson(requestJson, PublishRemoteStreamRequest.class);
    }

    private Mono<byte[]> resolveIpWithProxyCheckAsync(GatewayRequestHeader header, byte[] ipAddress) {
        return Mono.fromCallable(
                () -> resolveIpWithProxyCheck(header, ipAddress)
        );
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
