package bmv.pushca.binary.proxy.config;

import bmv.pushca.binary.proxy.encryption.EncryptionECService;
import bmv.pushca.binary.proxy.encryption.EncryptionService;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

  @Value("${binary-proxy.encryption.keys.path:/data/}")
  private String jwtEncryptionPathToKeys;

  @Value("${binary-proxy.encryption.private-key.pwd}")
  private String jwtEncryptionPrivateKeyPassword;

  @Bean
  public EncryptionService encryptionService() {
    try {
      return new EncryptionECService(
          jwtEncryptionPathToKeys,
          jwtEncryptionPrivateKeyPassword
      );
    } catch (Exception ex) {
      LOGGER.error("Cannot initialize encryption service", ex);
      return new EncryptionService() {
        @Override
        public <T> String encrypt(T input) throws Exception {
          throw new IllegalStateException("Encryption service is not initialised");
        }

        @Override
        public <T> byte[] encryptToBinary(T input) throws Exception {
          throw new IllegalStateException("Encryption service is not initialised");
        }

        @Override
        public <T> T decrypt(String encString, Class<T> clazz) throws Exception {
          throw new IllegalStateException("Encryption service is not initialised");
        }

        @Override
        public <T> T decryptFromBinary(byte[] input, Class<T> clazz) throws Exception {
          throw new IllegalStateException("Encryption service is not initialised");
        }

        @Override
        public String encryptJwt(JWTClaimsSet jwtClaims) {
          throw new IllegalStateException("Encryption service is not initialised");
        }

        @Override
        public JWTClaimsSet decryptAsClaims(String encJwtString) {
          throw new IllegalStateException("Encryption service is not initialised");
        }
      };
    }
  }

}
