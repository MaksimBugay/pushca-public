package bmv.pushca.binary.proxy.api.response;

import java.util.Map;

public record PageIdResponse(String pageId, Map<String, String> claims, String error) {
    public PageIdResponse(String pageId) {
        this(pageId, null, null);
    }
}
