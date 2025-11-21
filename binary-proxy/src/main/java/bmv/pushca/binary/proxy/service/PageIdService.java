package bmv.pushca.binary.proxy.service;

import bmv.pushca.binary.proxy.config.PushcaClusterSecretAware;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class PageIdService {

    private final EncryptionService encryptionService;

    private final String pushcaClusterSecret;

    public PageIdService(EncryptionService encryptionService, PushcaClusterSecretAware pushcaConfig) {
        this.encryptionService = encryptionService;
        this.pushcaClusterSecret = pushcaConfig.getPushcaClusterSecret();
    }

    public String generatePageId(String apiKey,
                                 String clientIp,
                                 String sessionId,
                                 Map<String, String> claims) {
        Map<String, String> allClaims = new HashMap<>();
        allClaims.put("k", apiKey);
        allClaims.put("ip", clientIp);
        allClaims.put("sId", sessionId == null ? UUID.randomUUID().toString() : sessionId);
        allClaims.put("t", String.valueOf(Instant.now().getEpochSecond()));
        if (MapUtils.isNotEmpty(claims)) {
            allClaims.putAll(claims);
        }
        try {
            return encryptionService.encryptString(
                    JsonUtility.toJson(allClaims)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> decryptPageId(String pageId, String apiKey) {
        if (StringUtils.isEmpty(apiKey)) {
            throw new RuntimeException("Forbidden: API key was not provided");
        }
        Map<String, String> claims = decryptPageId(pageId);
        if (pushcaClusterSecret.equals(apiKey)) {
            return claims;
        }
        if (!Objects.equals(claims.get("k"), apiKey)) {
            throw new RuntimeException("Forbidden: API key for different account was provided");
        }
        return claims;
    }

    public Map<String, String> decryptPageId(String pageId) {
        byte[] jsonBytes;
        try {
            jsonBytes = encryptionService.decryptToBytes(pageId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return JsonUtility.fromJsonToMap(
                jsonBytes,
                String.class,
                String.class
        );
    }

    public boolean verifyPageId(Map<String, String> pageIdClaims,
                                String apiKey,
                                String clientIp,
                                String sessionId,
                                long ttlSec) {
        return (pushcaClusterSecret.equals(apiKey) || Objects.equals(pageIdClaims.get("k"), apiKey))
                && Objects.equals(pageIdClaims.get("ip"), clientIp)
                && Objects.equals(pageIdClaims.get("sId"), sessionId)
                && (
                (Instant.now().getEpochSecond() - Long.parseLong(pageIdClaims.get("t"))) <= ttlSec
        );
    }
}
