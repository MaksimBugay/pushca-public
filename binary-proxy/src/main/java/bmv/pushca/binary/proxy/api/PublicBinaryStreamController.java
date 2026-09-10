package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.util.BinaryUtils.canPlayTypeInBrowser;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import bmv.pushca.binary.proxy.pushca.exception.CannotDownloadBinaryChunkException;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.util.NetworkUtils;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.PublicBinaryChunkService;
import bmv.pushca.binary.proxy.service.WebsocketPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Public binary playback with HTTP byte ranges, independent of the legacy download endpoints.
 */
@RestController
public class PublicBinaryStreamController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicBinaryStreamController.class);

    private final BinaryProxyService binaryProxyService;
    private final PublicBinaryChunkService publicBinaryChunkService;
    private final WebsocketPool websocketPool;

    public PublicBinaryStreamController(BinaryProxyService binaryProxyService,
                                        PublicBinaryChunkService publicBinaryChunkService,
                                        WebsocketPool websocketPool) {
        this.binaryProxyService = binaryProxyService;
        this.publicBinaryChunkService = publicBinaryChunkService;
        this.websocketPool = websocketPool;
    }

    @GetMapping("/binary/stream/{workspaceId}/{binaryId}/{binaryName}")
    public Flux<byte[]> servePublicBinaryAsStreamWithName(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("binaryId") String binaryId,
            @PathVariable("binaryName") String binaryName,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
            @RequestParam(value = "page-id", required = false) String pageId,
            @RequestParam(value = "human-token", required = false) String humanToken,
            @RequestParam(value = "for-download-only", required = false) String forDownloadOnly,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        String receiverIP = NetworkUtils.getRealIP(xForwardedFor, xRealIp);
        // Without a representation validator, If-Range must fall back to a full response.
        String rangeHeader = request.getMethod() == HttpMethod.GET
                && request.getHeaders().getFirst(HttpHeaders.IF_RANGE) == null
                ? request.getHeaders().getFirst(HttpHeaders.RANGE) : null;
        boolean headOnly = request.getMethod() == HttpMethod.HEAD;
        final ConcurrentLinkedQueue<String> pendingChunks = new ConcurrentLinkedQueue<>();
        final String downloadSessionId = UUID.randomUUID().toString();
        return binaryProxyService.requestBinaryManifestWithHumanOnlyCheck(
                        workspaceId,
                        binaryId,
                        pageId,
                        humanToken,
                        response::setStatusCode
                )
                .onErrorResume(throwable -> Mono.error(
                        new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)))
                .flatMapMany(binaryManifest -> {
                            String mimeType = binaryManifest.mimeType();
                            if (mimeType == null || "true".equals(forDownloadOnly)) {
                                mimeType = "application/octet-stream";
                            }
                            LOGGER.info("Transfer binary: sender IP {}, receiver IP {}, name {}, mime-type {}, size {}",
                                    binaryManifest.senderIP(), receiverIP,
                                    binaryManifest.name(),
                                    mimeType,
                                    binaryManifest.getTotalSize());
                            // Set the Content-Disposition header to suggest the filename for the download
                            if (!canPlayTypeInBrowser(mimeType)) {
                                response.getHeaders().setContentDisposition(
                                        ContentDisposition.builder("attachment")
                                                .filename(binaryManifest.name())
                                                .build()
                                );
                            }
                            long totalSize = binaryManifest.getTotalSize();
                            long start = 0;
                            long end = totalSize - 1;
                            response.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
                            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                                try {
                                    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                                    // Multiple ranges may be ignored; browser media seeking uses a single range.
                                    if (ranges.size() == 1) {
                                        start = ranges.getFirst().getRangeStart(totalSize);
                                        end = ranges.getFirst().getRangeEnd(totalSize);
                                        if (start >= totalSize || start > end) {
                                            throw new IllegalArgumentException("Unsatisfiable byte range");
                                        }
                                        response.setStatusCode(HttpStatus.PARTIAL_CONTENT);
                                        response.getHeaders().set(HttpHeaders.CONTENT_RANGE,
                                                "bytes " + start + "-" + end + "/" + totalSize);
                                    }
                                } catch (IllegalArgumentException ex) {
                                    response.setStatusCode(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                                    response.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize);
                                    response.getHeaders().setContentLength(0);
                                    return Flux.empty();
                                }
                            }
                            response.getHeaders().setContentLength(end - start + 1);
                            response.getHeaders().set("X-Total-Size", String.valueOf(totalSize));
                            // Set the Content-Type header
                            if (isNotEmpty(binaryManifest.mimeType())) {
                                response.getHeaders().setContentType(MediaType.valueOf(binaryManifest.mimeType()));
                            } else {
                                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                            }
                            if (headOnly) {
                                return Flux.empty();
                            }
                            List<ChunkSlice> chunks = new ArrayList<>();
                            long offset = 0;
                            for (Datagram datagram : binaryManifest.datagrams().stream()
                                    .sorted(Comparator.comparingInt(Datagram::order)).toList()) {
                                long nextOffset = offset + datagram.size();
                                if (offset > end) {
                                    break;
                                }
                                if (nextOffset > start && datagram.size() > 0) {
                                    chunks.add(new ChunkSlice(datagram,
                                            (int) Math.max(0, start - offset),
                                            (int) Math.min(datagram.size(), end - offset + 1)));
                                }
                                offset = nextOffset;
                            }
                            return Flux.fromIterable(chunks)
                                    .concatMap(
                                            chunk -> Mono.fromFuture(() ->
                                                            publicBinaryChunkService.requestBinaryChunk(
                                                                    workspaceId,
                                                                    downloadSessionId,
                                                                    binaryId,
                                                                    chunk.datagram(),
                                                                    pendingChunks)
                                                    )
                                                    .map(chunk::slice)
                                                    .onErrorResume(throwable -> Mono.error(
                                                                    new CannotDownloadBinaryChunkException(
                                                                            binaryId, chunk.datagram(),
                                                                            downloadSessionId,
                                                                            throwable
                                                                    )
                                                            )
                                                    )
                                    );
                        }
                )
                .onErrorResume(
                        throwable -> {
                            if (throwable instanceof CannotDownloadBinaryChunkException) {
                                if ((throwable.getCause() != null)
                                        && (throwable.getCause() instanceof TimeoutException)) {
                                    LOGGER.error("Failed by timeout attempt to download binary with id {}",
                                            binaryId, throwable);
                                    response.setStatusCode(HttpStatus.NOT_FOUND);
                                    return Mono.empty();
                                } else {
                                    return Mono.error(throwable);
                                }
                            } else {
                                return Mono.error(new RuntimeException("Error fetching binary data", throwable));
                            }
                        })
                .doFinally(
                        _ -> {
                            binaryProxyService.removeDownloadSession(binaryId, downloadSessionId);
                            pendingChunks.forEach(websocketPool::removeResponseWaiter);
                            pendingChunks.clear();
                        }
                );
    }

    private record ChunkSlice(Datagram datagram, int from, int to) {
        byte[] slice(byte[] bytes) {
            return from == 0 && to == bytes.length ? bytes : Arrays.copyOfRange(bytes, from, to);
        }
    }
}
