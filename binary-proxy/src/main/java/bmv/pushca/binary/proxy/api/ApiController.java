package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.util.BinaryUtils.canPlayTypeInBrowser;
import static bmv.pushca.binary.proxy.util.BinaryUtils.isDownloadBinaryRequestExpired;
import static bmv.pushca.binary.proxy.util.BinaryUtils.patchPrivateUrlSuffix;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import bmv.pushca.binary.proxy.pushca.exception.CannotDownloadBinaryChunkException;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.util.NetworkUtils;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ApiController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketPool.class);
  private static final String REDIRECT_URL_WITH_WORKSPACE_PATTERN =
      "/protected-binary.html?suffix={0}&workspace={1}";

  private static final String REDIRECT_URL_PATTERN = "/protected-binary.html?suffix={0}";

  private final WebsocketPool websocketPool;
  private final BinaryProxyService binaryProxyService;

  private final EncryptionService encryptionService;

  @Autowired
  public ApiController(
      WebsocketPool websocketPool, BinaryProxyService binaryProxyService,
      EncryptionService encryptionService) {
    this.websocketPool = websocketPool;
    this.binaryProxyService = binaryProxyService;
    this.encryptionService = encryptionService;
  }

  @PostMapping(value = "/binary/admin/recreate-ws-pool")
  public Mono<Void> reCreateWebsocketPool() {
    return Mono.fromRunnable(() -> {
      websocketPool.closeNettyWebsocketPool();
      websocketPool.createNettyWebsocketPool();
    });
  }

  @CrossOrigin(origins = "*")
  @PostMapping(value = "/binary/private/create-url-suffix")
  public Mono<String> createPrivateUrlSuffix(@RequestBody CreatePrivateUrlSuffixRequest request) {
    return Mono.just(MessageFormat.format("{0}{1}{2}",
            encryptionService.encryptBytes(
                binaryProxyService.generatePrivateUrlShortSuffix(
                    request.workspaceId(), request.binaryId()
                ),
                RuntimeException::new
            ),
            MESSAGE_PARTS_DELIMITER,
            encryptionService.encrypt(request, RuntimeException::new)
        ))
        .onErrorResume(Mono::error);
  }

  @CrossOrigin(origins = "*", exposedHeaders = "Content-Disposition")
  @PostMapping(value = "/binary/protected")
  public Flux<byte[]> serveProtectedBinaryAsStream(
      @RequestBody DownloadProtectedBinaryRequest request,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    CreatePrivateUrlSuffixRequest params;
    try {
      params = encryptionService.decrypt(request.suffix(), CreatePrivateUrlSuffixRequest.class);
    } catch (Exception ex) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return Flux.empty();
    }

    final ClientSearchData ownerFilter = new ClientSearchData(
        params.workspaceId(),
        null,
        null,
        null,
        true,
        List.of()
    );
    CompletableFuture<Boolean> verificationFuture;
    if (isDownloadBinaryRequestExpired(request.exp())) {
      verificationFuture = CompletableFuture.completedFuture(Boolean.FALSE);
    } else {
      CompletableFuture<Boolean> validatePasswordHashFuture;
      if (StringUtils.isEmpty(request.passwordHash())) {
        validatePasswordHashFuture = CompletableFuture.completedFuture(Boolean.FALSE);
      } else {
        validatePasswordHashFuture = binaryProxyService.validatePasswordHash(
            params.binaryId(),
            request.passwordHash(),
            ownerFilter
        );
      }

      verificationFuture = validatePasswordHashFuture.thenCompose(isPasswordHashValid -> {
        if (!isPasswordHashValid) {
          LOGGER.warn("Password hash validation was not passed");
          return binaryProxyService.verifyBinarySignature(
              ownerFilter,
              new DownloadProtectedBinaryRequest(
                  request.suffix(),
                  request.exp(),
                  request.signature(),
                  params.binaryId(),
                  null
              )
          );
        } else {
          return CompletableFuture.completedFuture(Boolean.TRUE);
        }
      });
    }

    return Mono.fromFuture(verificationFuture)
        .flatMapMany(isValid -> {
          if (isValid) {
            return serveBinaryAsStream(params.workspaceId(), params.binaryId(), response, true,
                NetworkUtils.getRealIP(xForwardedFor, xRealIp));
          } else {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return Mono.empty();
          }
        })
        .onErrorResume(throwable -> {
          if (throwable instanceof TimeoutException) {
            response.setStatusCode(NOT_FOUND);
          } else {
            LOGGER.warn("Tampered signature for binary with id: {}", params.binaryId(), throwable);
            response.setStatusCode(HttpStatus.FORBIDDEN);
          }
          return Mono.empty();
        });
  }

  @GetMapping(value = "/binary/{binaryCoordinates}")
  public Mono<Void> redirectToProtectedBinary(
      @PathVariable String binaryCoordinates,
      @RequestParam(value = "workspace", required = false) String workspaceId,
      ServerHttpResponse response) {

    return Mono.fromFuture(binaryProxyService.getPrivateUrlSuffix(binaryCoordinates))
        .flatMap(suffix0 -> {
          String patchedSuffix = patchPrivateUrlSuffix(suffix0);
          String url;
          if (StringUtils.isEmpty(workspaceId)) {
            url = MessageFormat.format(REDIRECT_URL_PATTERN, patchedSuffix);
          } else {
            url = MessageFormat.format(REDIRECT_URL_WITH_WORKSPACE_PATTERN, patchedSuffix, workspaceId);
          }
          response.setStatusCode(HttpStatus.FOUND);
          setResponseLocation(response, url);
          return response.setComplete();
        })
        .onErrorResume(
            throwable -> {
              if ((throwable.getCause() != null)
                  && (throwable.getCause() instanceof TimeoutException)) {
                LOGGER.error("Failed by timeout attempt to access binary with id {}",
                    binaryCoordinates, throwable);
                response.setStatusCode(HttpStatus.NOT_FOUND);
              } else {
                LOGGER.error("Failed attempt to access binary with id {}", binaryCoordinates,
                    throwable);
                response.setStatusCode(HttpStatus.EXPECTATION_FAILED);
              }
              return response.setComplete();
            });
  }

  private void setResponseLocation(ServerHttpResponse response, String url) {
    try {
      response.getHeaders().setLocation(new URI(url));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @GetMapping(value = "/binary/{workspaceId}/{binaryId}")
  public Flux<byte[]> servePublicBinaryAsStream(
      @PathVariable String workspaceId,
      @PathVariable String binaryId,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    return serveBinaryAsStream(workspaceId, binaryId, response, false,
        NetworkUtils.getRealIP(xForwardedFor, xRealIp));
  }

  private Flux<byte[]> serveBinaryAsStream(String workspaceId, String binaryId,
      ServerHttpResponse response, boolean securePost, String receiverIP) {
    final ConcurrentLinkedQueue<String> pendingChunks = new ConcurrentLinkedQueue<>();
    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .onErrorResume(throwable -> Mono.error(
            new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)))
        .flatMapMany(binaryManifest -> {
              LOGGER.info("Transfer binary: sender IP {}, receiver IP {}, name {}, size {}",
                  binaryManifest.senderIP(), receiverIP, binaryManifest.name(),
                  binaryManifest.getTotalSize());
              // Set the Content-Disposition header to suggest the filename for the download
              if (securePost || (!canPlayTypeInBrowser(binaryManifest.mimeType()))) {
                response.getHeaders().setContentDisposition(
                    ContentDisposition.builder("attachment")
                        .filename(binaryManifest.name())
                        .build()
                );
              }
              // Set the Content-Length header if the total size is known
              response.getHeaders().setContentLength(
                  binaryManifest.getTotalSize()
              );
              // Set the Content-Type header
              if (StringUtils.isNotEmpty(binaryManifest.mimeType())) {
                response.getHeaders().setContentType(MediaType.valueOf(binaryManifest.mimeType()));
              } else {
                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
              }
              return Flux.fromIterable(binaryManifest.datagrams())
                  .flatMap(
                      dtm -> Mono.fromFuture(
                              binaryProxyService.requestBinaryChunk(
                                  workspaceId,
                                  binaryManifest.downloadSessionId(),
                                  binaryId,
                                  dtm,
                                  binaryManifest.datagrams().size() - 1,
                                  pendingChunks)
                          )
                          .onErrorResume(throwable -> Mono.error(
                                  new CannotDownloadBinaryChunkException(
                                      binaryId, dtm,
                                      binaryManifest.downloadSessionId(),
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
                binaryProxyService.removeDownloadSession(
                    ((CannotDownloadBinaryChunkException) throwable).binaryId,
                    ((CannotDownloadBinaryChunkException) throwable).downloadSessionId
                );
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
        .doFinally((signalType) -> {
          pendingChunks.forEach(websocketPool::removeResponseWaiter);
          pendingChunks.clear();
        });
  }
}
