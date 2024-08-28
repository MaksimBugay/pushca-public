package bmv.pushca.binary.proxy.encryption;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.function.Function;

public interface EncryptionService {

  <T> String encrypt(T input) throws Exception;

  String encryptString(String inputStr) throws Exception;

  default <T> String encrypt(T input, Function<Exception, RuntimeException> exceptionBuilder) {
    try {
      return encrypt(input);
    } catch (Exception e) {
      throw exceptionBuilder.apply(e);
    }
  }

  <T> byte[] encryptToBinary(T input) throws Exception;

  <T> T decrypt(String encString, Class<T> clazz) throws Exception;

  default <T> T decryptPipeSafe(String encString, Class<T> clazz) {
    try{
      return decrypt(encString, clazz);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  String decryptString(String encString) throws Exception;

  <T> T decryptFromBinary(byte[] input, Class<T> clazz) throws Exception;

  String encryptJwt(JWTClaimsSet jwtClaims) throws Exception;

  default String encryptJwt(String jwtString) throws Exception {
    JWTClaimsSet jwtClaims = JWTClaimsSet.parse(jwtString);
    return encryptJwt(jwtClaims);
  }

  JWTClaimsSet decryptAsClaims(String encJwtString) throws Exception;

  default String decryptJwt(String encJwtString) throws Exception {
    return decryptAsClaims(encJwtString).toString();
  }

  enum Algorithm {EC, RSA}
}
