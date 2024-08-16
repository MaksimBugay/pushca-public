package bmv.pushca.binary.proxy.encryption.util;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.slf4j.LoggerFactory.getLogger;

import eu.emi.security.authn.x509.impl.CertificateUtils;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.IESParameterSpec;
import org.slf4j.Logger;

/**
 * Data Encryption routines.
 */
public class EncUtil {

  private static final Logger LOGGER = getLogger(EncUtil.class);

  public static final String EC_KEY_FACTORY = "EC";
  public static final String EC_CIPHER = "ECIESwithAES-CBC";
  private static final String EC_CURVE = "c2pnb368w1";
  private static final IESParameterSpec EC_PARAM_SPEC;

  public static final ThreadLocal<SecureRandom> SRND =
      ThreadLocal.withInitial(EncUtil::getSecureRandomStrong);

  static {
    LOGGER.debug("Encryption utility initialization");

    byte[] nonce =
        new byte[] {13, -117, -10, -78, -10, -55, 117, -71, 91, 52, -124, 105, 89, 87, -73, -16};
    //byte[] nonce = new byte[16];
    //SecureRandom secureRandom = SRND.get();
    //secureRandom.nextBytes(nonce);

    EC_PARAM_SPEC = new IESParameterSpec(null, null, 128, 128, nonce, true);

    try {
      boolean limit = Cipher.getMaxAllowedKeyLength("RC5") < 256;
      if (limit) {
        LOGGER.error("limited crypto policy file is used!");
      }
    } catch (NoSuchAlgorithmException e) {
      LOGGER.error("Failed Encryption utility initialization attempt", e);
    }

    try {
      CertificateUtils.configureSecProvider();
    } catch (Throwable throwable) {
      LOGGER.error("can't init BC {} > {}",
          throwable.getClass().getName(), throwable.getMessage());
    }
  }

  /**
   * Due to a buggy implementation of /dev/random on Linux crypto code
   * becomes extremely slow there. On other operating systems use standard
   * strongest instance of randomness.
   *
   * @see <a href="https://www.synopsys.com/blogs/software-security/proper-use-of-javas-securerandom/">
   *          Proper use of Java SecureRandom</a>
   */
  private static SecureRandom getSecureRandomStrong() {
    try {
      return IS_OS_LINUX ? new SecureRandom()
          : SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
  }

  /** Generates EC public/private key pair */
  public static KeyPair generateKeyPair()
      throws NoSuchProviderException, NoSuchAlgorithmException,
      InvalidAlgorithmParameterException {
    KeyPairGenerator g = KeyPairGenerator.getInstance(
        EC_KEY_FACTORY, BouncyCastleProvider.PROVIDER_NAME);

    g.initialize(ECNamedCurveTable.getParameterSpec(EC_CURVE), SRND.get());

    KeyPair pair = g.generateKeyPair();

    if (!"X.509".equals(pair.getPublic().getFormat())) {
      throw new IllegalStateException();
    }

    if (!"PKCS#8".equals(pair.getPrivate().getFormat())) {
      throw new IllegalStateException();
    }

    return pair;
  }

  public static byte[] encodeData(PublicKey key, byte[] in)
      throws GeneralSecurityException {
    return transformData(ENCRYPT_MODE, key, in);
  }

  public static byte[] decodeData(PrivateKey parent, byte[] data)
      throws GeneralSecurityException {
    return transformData(DECRYPT_MODE, parent, data);
  }

  private static byte[] transformData(int opMode, Key key, byte[] data)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(EC_CIPHER);
    cipher.init(opMode, key, EC_PARAM_SPEC, SRND.get());
    return cipher.doFinal(data);
  }
}
