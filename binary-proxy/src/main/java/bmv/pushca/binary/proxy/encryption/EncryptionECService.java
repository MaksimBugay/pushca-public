package bmv.pushca.binary.proxy.encryption;

import com.nimbusds.jwt.JWTClaimsSet;

public class EncryptionECService extends EncryptionServiceBase {

  private final ECCService eccService;

  public EncryptionECService(String privateKey, String publicKey, String privateKeyPassword)
      throws Exception {
    super(Algorithm.EC, privateKey, publicKey, privateKeyPassword);
    eccService = new ECCService(this.privateKey, this.publicKey);
  }

  public EncryptionECService(String pathToKeys, String privateKeyPassword)
      throws Exception {
    super(Algorithm.EC, pathToKeys, privateKeyPassword);
    eccService = new ECCService(privateKey, publicKey);
  }

  @Override
  public JWTClaimsSet decryptAsClaims(String encJwtString) throws Exception {
    String claims = eccService.decrypt(encJwtString, String.class);
    return JWTClaimsSet.parse(claims);
  }

  @Override
  public <T> String encrypt(T input) throws Exception {
    return eccService.encrypt(input);
  }

  @Override
  public <T> byte[] encryptToBinary(T input) throws Exception {
    return eccService.encryptToBinary(input);
  }

  @Override
  public <T> T decrypt(String encString, Class<T> clazz) throws Exception {
    return eccService.decrypt(encString, clazz);
  }

  @Override
  public <T> T decryptFromBinary(byte[] input, Class<T> clazz) throws Exception {
    return eccService.decryptFromBinary(input, clazz);
  }

  @Override
  public String encryptJwt(JWTClaimsSet jwtClaims) throws Exception {
    return eccService.encrypt(jwtClaims.toString());
  }
}
