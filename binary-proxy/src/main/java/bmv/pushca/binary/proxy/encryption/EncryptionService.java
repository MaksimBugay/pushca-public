package bmv.pushca.binary.proxy.encryption;

import com.nimbusds.jwt.JWTClaimsSet;

public interface EncryptionService {

    String encrypt(JWTClaimsSet jwtClaims) throws Exception;

    default String encrypt(String jwtString) throws Exception {
        JWTClaimsSet jwtClaims = JWTClaimsSet.parse(jwtString);
        return encrypt(jwtClaims);
    }

    JWTClaimsSet decryptAsClaims(String encJwtString) throws Exception;

    default String decrypt(String encJwtString) throws Exception {
        return decryptAsClaims(encJwtString).toString();
    }

    enum Algorithm { EC, RSA }
}
