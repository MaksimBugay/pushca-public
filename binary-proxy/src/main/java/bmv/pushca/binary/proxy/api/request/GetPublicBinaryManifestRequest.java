package bmv.pushca.binary.proxy.api.request;

public record GetPublicBinaryManifestRequest(
    String workspaceId,
    String binaryId,
    String pageId,
    String humanToken
) {

}
