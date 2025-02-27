package bmv.pushca.binary.proxy.api;

import static bmv.pushca.binary.proxy.pushca.PushcaMessageFactory.MESSAGE_PARTS_DELIMITER;
import static bmv.pushca.binary.proxy.util.BinaryUtils.canPlayTypeInBrowser;
import static bmv.pushca.binary.proxy.util.BinaryUtils.isDownloadBinaryRequestExpired;
import static bmv.pushca.binary.proxy.util.BinaryUtils.patchPrivateUrlSuffix;
import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.toJson;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.api.request.GetPublicBinaryManifestRequest;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import bmv.pushca.binary.proxy.pushca.exception.CannotDownloadBinaryChunkException;
import bmv.pushca.binary.proxy.pushca.exception.InvalidHumanTokenException;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.util.NetworkUtils;
import bmv.pushca.binary.proxy.service.BinaryProxyService;
import bmv.pushca.binary.proxy.service.IpGeoLookupService;
import bmv.pushca.binary.proxy.service.WebClientFactory;
import bmv.pushca.binary.proxy.service.WebsocketPool;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
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
import org.springframework.web.reactive.function.client.WebClient;
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

  private final IpGeoLookupService ipGeoLookupService;
  private final WebClient webClient = WebClientFactory.createWebClient();

  @Autowired
  public ApiController(
      WebsocketPool websocketPool, BinaryProxyService binaryProxyService,
      EncryptionService encryptionService, IpGeoLookupService ipGeoLookupService) {
    this.websocketPool = websocketPool;
    this.binaryProxyService = binaryProxyService;
    this.encryptionService = encryptionService;
    this.ipGeoLookupService = ipGeoLookupService;
  }

  @PostMapping(value = "/binary/admin/recreate-ws-pool")
  public Mono<Void> reCreateWebsocketPool() {
    return Mono.fromRunnable(() -> {
      websocketPool.closeNettyWebsocketPool();
      websocketPool.createNettyWebsocketPool();
    });
  }

  @CrossOrigin(origins = "*")
  @PostMapping(value = "/binary/resolve-ip")
  Mono<GeoLookupResponse> resolveIp(@RequestBody ResolveIpRequest request) {
    return webClient.post()
        .uri("https://app.multiloginapp.com/resolve") // Replace with the actual external API URL
        .bodyValue(request) // Pass the request body
        .retrieve()
        .bodyToMono(GeoLookupResponse.class) // Convert response to desired type
        .onErrorResume(error -> Mono.error(
            new RuntimeException("Failed geo lookup attempt for ip" + request.ip(), error)));
  }

  @CrossOrigin(origins = "*")
  @PostMapping(value = "/binary/resolve-ip-with-proxy-check")
  Mono<GeoLookupResponse> resolveIpWithProxyCheck(@RequestBody ResolveIpRequest request) {
    return Mono.fromCallable(() -> ipGeoLookupService.resolve(request.ip()))
        .onErrorResume(error -> Mono.error(
            new RuntimeException("Failed geo lookup attempt for ip" + request.ip(), error)));
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

    final ClientSearchData ownerFilter = binaryProxyService.buildClientFilter(
        params.workspaceId(),
        true
    );
    CompletableFuture<Boolean> verificationFuture;
    if (isDownloadBinaryRequestExpired(request.exp())) {
      verificationFuture = CompletableFuture.completedFuture(Boolean.FALSE);
    } else {
      CompletableFuture<Boolean> validatePasswordHashFuture;
      if (isEmpty(request.passwordHash())) {
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

  @PostMapping(value = "/binary/m/protected")
  public Mono<BinaryManifest> getProtectedBinaryManifest(
      @RequestBody DownloadProtectedBinaryRequest request,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    CreatePrivateUrlSuffixRequest params;
    try {
      params = encryptionService.decrypt(request.suffix(), CreatePrivateUrlSuffixRequest.class);
    } catch (Exception ex) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return Mono.empty();
    }

    final ClientSearchData ownerFilter = binaryProxyService.buildClientFilter(
        params.workspaceId(),
        true
    );
    CompletableFuture<Boolean> verificationFuture;
    if (isDownloadBinaryRequestExpired(request.exp())) {
      verificationFuture = CompletableFuture.completedFuture(Boolean.FALSE);
    } else {
      CompletableFuture<Boolean> validatePasswordHashFuture;
      if (isEmpty(request.passwordHash())) {
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
        .flatMap(isValid -> {
          if (isValid) {
            return getBinaryManifest(params.workspaceId(), params.binaryId(), response,
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
          if (isEmpty(workspaceId)) {
            url = MessageFormat.format(REDIRECT_URL_PATTERN, patchedSuffix);
          } else {
            url = MessageFormat.format(REDIRECT_URL_WITH_WORKSPACE_PATTERN, patchedSuffix,
                workspaceId);
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

  @GetMapping(value = "/binary/binary-manifest/protected/{suffix}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<String> getProtectedBinaryDescription(
      @PathVariable String suffix,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response
  ) {
    CreatePrivateUrlSuffixRequest params;
    try {
      params = encryptionService.decrypt(suffix, CreatePrivateUrlSuffixRequest.class);
    } catch (Exception ex) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return Mono.empty();
    }

    return getBinaryDescription(
        params.workspaceId(),
        params.binaryId(),
        xForwardedFor,
        xRealIp,
        response
    );
  }

  @GetMapping(value = "/binary/binary-manifest/{workspaceId}/{binaryId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<String> getBinaryDescription(
      @PathVariable String workspaceId,
      @PathVariable String binaryId,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    String ip = NetworkUtils.getRealIP(xForwardedFor, xRealIp);
    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .map(manifest -> {
          LOGGER.debug("Binary manifest was acquired by user with IP {}: {}", ip, toJson(manifest));
          return manifest;
        })
        .map(manifest -> isNotEmpty(manifest.readMeText()) ? manifest.readMeText()
            : "No description")
        .onErrorResume(
            throwable -> {
              if ((throwable.getCause() != null)
                  && (throwable.getCause() instanceof TimeoutException)) {
                LOGGER.error("Failed by timeout attempt to download manifest for binary with id {}",
                    binaryId, throwable);
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return Mono.empty();
              } else {
                LOGGER.error("Error fetching binary manifest for binary with id: {}", binaryId,
                    throwable);
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return Mono.just("{\"error\": \"Unable to fetch binary manifest\"}");
              }
            }
        );
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

  @GetMapping(value = "/binary/m/{workspaceId}/{binaryId}")
  public Mono<BinaryManifest> getPublicBinaryManifest(
      @PathVariable String workspaceId,
      @PathVariable String binaryId,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    return getBinaryManifest(workspaceId, binaryId, response,
        NetworkUtils.getRealIP(xForwardedFor, xRealIp));
  }

  @PostMapping(value = "/binary/m/public")
  public Mono<BinaryManifest> getPublicBinaryManifest(
      @RequestBody GetPublicBinaryManifestRequest request,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
      ServerHttpResponse response) {
    return getBinaryManifest(
        request.workspaceId(),
        request.binaryId(),
        response,
        NetworkUtils.getRealIP(xForwardedFor, xRealIp),
        request.pageId(),
        request.humanToken()
    );
  }

  private Mono<Boolean> validateHumanToken(String pageId, String token) {
    return webClient.post()
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

  private Mono<BinaryManifest> getBinaryManifest(String workspaceId, String binaryId,
      ServerHttpResponse response, String receiverIP) {
    return getBinaryManifest(workspaceId, binaryId, response, receiverIP, null, null);
  }

  private Mono<BinaryManifest> getBinaryManifest(String workspaceId, String binaryId,
      ServerHttpResponse response, String receiverIP, String pageId, String humanToken) {
    return Mono.fromFuture(binaryProxyService.requestBinaryManifest(workspaceId, binaryId))
        .flatMap(binaryManifest -> {
          LOGGER.info("Transfer binary: sender IP {}, receiver IP {}, name {}, size {}",
              binaryManifest.senderIP(), receiverIP, binaryManifest.name(),
              binaryManifest.getTotalSize());

          if (Boolean.TRUE.equals(binaryManifest.forHuman())) {
            if (StringUtils.isEmpty(pageId) || StringUtils.isEmpty(humanToken)){
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
        .onErrorResume(throwable -> {
          if ((throwable.getCause() != null)
              && (throwable.getCause() instanceof TimeoutException)) {
            LOGGER.error("Failed by timeout attempt to download binary with id {}",
                binaryId, throwable);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return Mono.empty();
          } else if (throwable instanceof InvalidHumanTokenException) {
            LOGGER.warn("Invalid human token: page id {}, token {}", pageId, humanToken);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return Mono.empty();
          } else {
            return Mono.error(throwable);
          }
        })
        .onErrorResume(throwable -> Mono.error(
                new RuntimeException("Error fetching binary manifest: " + binaryId, throwable)
            )
        );
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
              /*response.getHeaders().setContentLength(
                  binaryManifest.getTotalSize()
              );*/
              response.getHeaders().set("X-Total-Size", String.valueOf(binaryManifest.getTotalSize()));
              // Set the Content-Type header
              if (isNotEmpty(binaryManifest.mimeType())) {
                response.getHeaders().setContentType(MediaType.valueOf(binaryManifest.mimeType()));
              } else {
                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
              }
              return Flux.fromIterable(
                      binaryManifest.datagrams().stream().sorted(Comparator.comparingInt(
                          Datagram::order)).collect(
                          Collectors.toList()))
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
