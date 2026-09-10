package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal.DEFAULT_CHUNK_SIZE;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

/** Fetches individual chunks for public playback without starting a sequential legacy download. */
@Service
public class PublicBinaryChunkService {
    private final WebsocketPool websocketPool;
    private final BinaryProxyService binaryProxyService;
    private final MicroserviceConfiguration microserviceConfiguration;
    private final int pushcaClientHashCode;

    public PublicBinaryChunkService(WebsocketPool websocketPool,
                                   BinaryProxyService binaryProxyService,
                                   MicroserviceConfiguration microserviceConfiguration,
                                   PushcaWsClientFactory pushcaWsClientFactory) {
        this.websocketPool = websocketPool;
        this.binaryProxyService = binaryProxyService;
        this.microserviceConfiguration = microserviceConfiguration;
        this.pushcaClientHashCode = pushcaWsClientFactory.pushcaClient.hashCode();
    }

    public CompletableFuture<byte[]> requestBinaryChunk(String workspaceId, String downloadSessionId,
                                                        String binaryId, Datagram datagram,
                                                        ConcurrentLinkedQueue<String> pendingChunks) {
        final String datagramId = buildDatagramId(binaryId, datagram.order(), pushcaClientHashCode);
        ResponseWaiter<byte[]> responseWaiter = new ResponseWaiter<>(
                (chunk) -> chunk.length == datagram.size()
                        && calculateSha256(chunk).equals(datagram.md5()),
                null,
                null,
                MessageFormat.format("Invalid chunk {0} of binary with id {1} was received",
                        String.valueOf(datagram.order()), binaryId),
                () -> binaryProxyService.sendUploadBinaryAppeal(
                        workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order())
                ),
                microserviceConfiguration.responseTimeoutMs,
                3L * microserviceConfiguration.responseTimeoutMs
        );

        final String waiterId = concatParts(datagramId, downloadSessionId);

        responseWaiter.whenComplete((_, _) -> {
            websocketPool.removeResponseWaiter(waiterId);
            pendingChunks.remove(waiterId);
        });

        websocketPool.registerResponseWaiter(
                waiterId, responseWaiter
        );
        pendingChunks.add(waiterId);

        // Each subscribed chunk is requested independently, including seeks past chunk zero.
        websocketPool.registerDownloadSession(binaryId, downloadSessionId);
        websocketPool.activateResponseWaiter(waiterId);
        binaryProxyService.sendUploadBinaryAppeal(
                workspaceId, binaryId, DEFAULT_CHUNK_SIZE, false, List.of(datagram.order())
        );

        return responseWaiter;
    }

}
