package bmv.pushca.binary.proxy.api.request;

import java.util.Map;

public record GeneratePageIdRequest(
        String apiKey,
        String clientIp,
        String sessionId,
        Map<String, String> claims
) {
}
