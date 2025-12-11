package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.ID_GENERATOR;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.PRIVATE_URL_SUFFIX;
import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MessageType.VALIDATE_PASSWORD_HASH;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_GATEWAY_REQUEST;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_MESSAGE;
import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.intToBytes;

import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.exception.InvalidHumanTokenException;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils;
import bmv.pushca.binary.proxy.service.BinaryCoordinatesService.BinaryCoordinates;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;

import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class BinaryProxyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryProxyService.class);

    private final WebsocketPool websocketPool;

    private final int pushcaClientHashCode;

    private final PClient pushcaClient;

    private final MicroserviceConfiguration microserviceConfiguration;

    private final BinaryCoordinatesService binaryCoordinatesService;

    private final SplittableRandom random = new SplittableRandom();

    private final WebClient webClient = WebClientFactory.createWebClient();

    public BinaryProxyService(WebsocketPool websocketPool,
                              PushcaWsClientFactory pushcaWsClientFactory,
                              MicroserviceConfiguration microserviceConfiguration,
                              BinaryCoordinatesService binaryCoordinatesService) {
        this.pushcaClient = pushcaWsClientFactory.pushcaClient;
        this.pushcaClientHashCode = pushcaWsClientFactory.pushcaClient.hashCode();
        this.websocketPool = websocketPool;
        this.microserviceConfiguration = microserviceConfiguration;
        this.binaryCoordinatesService = binaryCoordinatesService;
    }

    public WebClient getWebClient() {
        return webClient;
    }

    public byte[] generatePrivateUrlShortSuffix(String workspaceId, String binaryId) {
        byte[] suffix = ArrayUtils.addAll(
                intToBytes(random.nextInt(0, Integer.MAX_VALUE)),
                intToBytes(BmvObjectUtils.calculateStringHashCode(workspaceId))
        );
        return ArrayUtils.addAll(
                suffix,
                intToBytes(BmvObjectUtils.calculateStringHashCode(binaryId))
        );
    }

    public CompletableFuture<Boolean> verifyBinarySignature(
            ClientSearchData ownerFilter, DownloadProtectedBinaryRequest signedRequest) {

        String id = sendVerifySignatureRequest(ownerFilter, signedRequest);
        return websocketPool.registerResponseWaiter(
                id, microserviceConfiguration.responseTimeoutMs
        );
    }

    public CompletableFuture<Boolean> validatePasswordHash(String binaryId, String passwordHash,
                                                           ClientSearchData ownerFilter) {
        String id = broadcastMessage(ownerFilter, MessageFormat.format("{0}::{1}::{2}",
                VALIDATE_PASSWORD_HASH.name(),
                binaryId,
                passwordHash
        ));
        return websocketPool.registerResponseWaiter(
                id, microserviceConfiguration.responseTimeoutMs
        );
    }

    public ClientSearchData buildClientFilter(String workspaceId, boolean findAny) {
        return buildClientFilter(BmvObjectUtils.calculateStringHashCode(workspaceId), findAny);
    }

    public ClientSearchData buildClientFilter(int workspaceIdHash, boolean findAny) {
        return new ClientSearchData(
                String.valueOf(workspaceIdHash),
                null,
                null,
                null,
                findAny,
                List.of(pushcaClient)
        );
    }

    public CompletableFuture<String> getPrivateUrlSuffix(String encBinaryCoordinates) {
        BinaryCoordinates coordinates = binaryCoordinatesService.retrieve(encBinaryCoordinates);
        final ClientSearchData dest = buildClientFilter(coordinates.workspaceIdHash(), false);
        String id = broadcastMessage(dest, MessageFormat.format("{0}::{1}",
                PRIVATE_URL_SUFFIX.name(),
                String.valueOf(coordinates.binaryIdHash())
        ));
        return websocketPool.registerResponseWaiter(
                id, microserviceConfiguration.responseTimeoutMs
        );
    }

    public Mono<BinaryManifest> requestBinaryManifestWithHumanOnlyCheck(
            String workspaceId, String binaryIdString, String pageId, String humanToken,
            Consumer<HttpStatus> httpStatusConsumer
    ) {
        return Mono.fromFuture(requestBinaryManifest(workspaceId, binaryIdString))
                .flatMap(binaryManifest -> {
                    if (Boolean.TRUE.equals(binaryManifest.forHuman())) {
                        if (StringUtils.isEmpty(pageId) || StringUtils.isEmpty(humanToken)) {
                            return Mono.error(new InvalidHumanTokenException());
                        } else {
                            return validateHumanToken(pageId, humanToken).handle((isHuman, sink) -> {
                                if (Boolean.TRUE.equals(isHuman)) {
                                    sink.next(binaryManifest);
                                } else {
                                    sink.error(new InvalidHumanTokenException());
                                }
                            });
                        }
                    } else {
                        return Mono.just(binaryManifest);
                    }
                })
                .doOnError(throwable -> {
                    if ((throwable.getCause() != null)
                            && (throwable.getCause() instanceof TimeoutException)) {
                        LOGGER.error("Failed by timeout attempt to download binary with id {}",
                                binaryIdString, throwable);
                        httpStatusConsumer.accept(HttpStatus.NOT_FOUND);
                    } else if (throwable instanceof InvalidHumanTokenException) {
                        LOGGER.warn("Invalid human token: page id {}, token {}", pageId, humanToken);
                        httpStatusConsumer.accept(HttpStatus.FORBIDDEN);
                    }
                })
                .onErrorResume(throwable -> {
                            if ((throwable.getCause() != null)
                                    && (throwable.getCause() instanceof TimeoutException)) {
                                return Mono.empty();
                            } else if (throwable instanceof InvalidHumanTokenException) {
                                return Mono.empty();
                            } else {
                                return Mono.error(
                                        new RuntimeException("Error fetching binary manifest: " + binaryIdString, throwable)
                                );
                            }
                        }
                );
    }

    private Mono<Boolean> validateHumanToken(String pageId, String token) {
        return getWebClient().post()
                .uri("https://secure.fileshare.ovh/pushca/dynamic-captcha/validate-human-token")
                .bodyValue(Map.of("pageId", pageId, "token", token))
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(error -> {
                            LOGGER.warn("Failed validate human token attempt", error);
                            return Mono.just(Boolean.FALSE);
                        }
                );
    }

    public CompletableFuture<BinaryManifest> requestBinaryManifest(String workspaceId,
                                                                   String binaryId) {
        CompletableFuture<BinaryManifest> future = websocketPool.registerResponseWaiter(
                binaryId, microserviceConfiguration.responseTimeoutMs
        );
        sendUploadBinaryAppeal(
                workspaceId, binaryId, DEFAULT_CHUNK_SIZE, true, null
        );

        return future;
    }

    public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String downloadSessionId,
                                                        String binaryId, Datagram datagram, int maxOrder,
                                                        ConcurrentLinkedQueue<String> pendingChunks) {
        final String datagramId = buildDatagramId(binaryId, datagram.order(), pushcaClientHashCode);
        ResponseWaiter<byte[]> responseWaiter = new ResponseWaiter<>(
                (chunk) -> chunk.length == datagram.size()
                        && calculateSha256(chunk).equals(datagram.md5()),
                null,
                null,
                MessageFormat.format("Invalid chunk {0} of binary with id {1} was received",
                        String.valueOf(datagram.order()), binaryId),
                () -> sendUploadBinaryAppeal(
                        workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order())
                ),
                microserviceConfiguration.responseTimeoutMs,
                (maxOrder + 1L) * microserviceConfiguration.responseTimeoutMs
        );

        final String waiterId = concatParts(datagramId, downloadSessionId);

        responseWaiter.whenComplete((bytes, error) -> {
            if (error == null) {
                pendingChunks.remove(waiterId);
                if (datagram.order() < maxOrder) {
                    final String nextDatagramId =
                            buildDatagramId(binaryId, datagram.order() + 1, pushcaClientHashCode);
                    websocketPool.activateResponseWaiter(concatParts(nextDatagramId, downloadSessionId));
                    sendUploadBinaryAppeal(
                            workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order() + 1)
                    );
                } else {
                    websocketPool.removeDownloadSession(binaryId, downloadSessionId);
                }
            }
        });

        websocketPool.registerResponseWaiter(
                waiterId, responseWaiter
        );
        pendingChunks.add(waiterId);

        if (datagram.order() == 0) {
            websocketPool.registerDownloadSession(binaryId, downloadSessionId);
            websocketPool.activateResponseWaiter(waiterId);
            sendUploadBinaryAppeal(
                    workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(0)
            );
        }

        return responseWaiter;
    }

    public void removeDownloadSession(String binaryId, String sessionId) {
        websocketPool.removeDownloadSession(binaryId, sessionId);
    }

    public String sendVerifySignatureRequest(ClientSearchData dest,
                                             DownloadProtectedBinaryRequest signedRequest) {
        return sendGatewayRequest(
                dest,
                false,
                "verify-binary-signature",
                JsonUtility.toJsonAsBytes(signedRequest)
        );
    }

    public String broadcastMessage(ClientSearchData dest, String message) {
        String id = ID_GENERATOR.generate().toString();

        Map<String, Object> metaData = new HashMap<>();
        metaData.put("filter", dest);
        metaData.put("message",
                MessageFormat.format("{0}::{1}", id, message));
        metaData.put("preserveOrder", false);

        websocketPool.sendCommand(null, SEND_MESSAGE, metaData);
        return id;
    }

    public String sendGatewayRequest(ClientSearchData dest,
                                     boolean preserveOrder, String path, byte[] requestPayload) {
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("receiver", dest);
        metaData.put("preserveOrder", preserveOrder);
        metaData.put("path", path);
        byte[] payload = requestPayload == null ? new byte[0] : requestPayload;
        metaData.put("payload", Base64.getEncoder().encodeToString(payload));

        return websocketPool.sendCommand(null, SEND_GATEWAY_REQUEST, metaData);
    }

    public void sendUploadBinaryAppeal(String workspaceId, String binaryId, int chunkSize,
                                       boolean manifestOnly, List<Integer> requestedChunks) {
        final ClientSearchData ownerFilter = buildClientFilter(workspaceId, false);
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("owner", ownerFilter);
        metaData.put("binaryId", binaryId);
        metaData.put("chunkSize", chunkSize);
        metaData.put("manifestOnly", manifestOnly);
        metaData.put("requestedChunks", requestedChunks);

        websocketPool.sendCommand(null, SEND_UPLOAD_BINARY_APPEAL, metaData);
    }

}
