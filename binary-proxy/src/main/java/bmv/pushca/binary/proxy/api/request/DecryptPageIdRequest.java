package bmv.pushca.binary.proxy.api.request;

public record DecryptPageIdRequest(
        String pageId,
        String apiKey
) {
}
