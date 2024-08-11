package bmv.pushca.binary.proxy.encryption;

import eu.emi.security.authn.x509.impl.CertificateUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public abstract class EncryptionServiceBase implements EncryptionService {

    protected final Algorithm algorithm;

    protected final PrivateKey privateKey;

    protected final PublicKey publicKey;

    protected EncryptionServiceBase(Algorithm algorithm, String privateKey, String publicKey,
        String privateKeyPassword) throws Exception {
        this.algorithm = algorithm;
        this.privateKey = loadPrivateKey(privateKey.getBytes(StandardCharsets.UTF_8), privateKeyPassword);
        this.publicKey = loadPublicKey(publicKey.getBytes(StandardCharsets.UTF_8));
    }

    protected EncryptionServiceBase(
            Algorithm algorithm, String pathToKeys, String privateKeyPassword)
            throws Exception {
        this.algorithm = algorithm;
        this.privateKey =
                loadPrivateKey(
                        pathToKeys + algorithm.name() + "/auth_private_key.pem",
                        privateKeyPassword);
        this.publicKey =
                loadPublicKey(
                        pathToKeys + algorithm.name() + "/auth_public_key.der");
    }

    PrivateKey loadPrivateKey(String fileName,
                                      String privateKeyPassword)
            throws Exception {

        byte[] keyBytes = Files.readAllBytes(Paths.get(fileName));
        return loadPrivateKey(keyBytes, privateKeyPassword);
    }

    PrivateKey loadPrivateKey(byte[] keyBytes,
                                      String privateKeyPassword)
            throws Exception {

        try (ByteArrayInputStream bis = new ByteArrayInputStream(keyBytes)) {
            return CertificateUtils.loadPrivateKey(
                    bis, CertificateUtils.Encoding.PEM,
                    privateKeyPassword.toCharArray());
        }
    }

    PublicKey loadPublicKey(String fileName)
            throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(fileName));
        return loadPublicKey(keyBytes);
    }

    PublicKey loadPublicKey(byte[] keyBytes)
            throws Exception {
        X509EncodedKeySpec spec =
                new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.name());
        return kf.generatePublic(spec);
    }

}
