package bmv.pushca.binary.proxy.encryption;

import static bmv.pushca.binary.proxy.encryption.util.EncUtil.decodeData;
import static bmv.pushca.binary.proxy.encryption.util.EncUtil.encodeData;
import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString;

import bmv.pushca.binary.proxy.util.serialisation.CBorUtility;
import java.security.PrivateKey;
import java.security.PublicKey;

public class ECCService {

    private final PrivateKey privateKey;

    private final PublicKey publicKey;

    public ECCService(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public <T> String encrypt(T input) throws Exception {
        return encodeBase64URLSafeString(encryptToBinary(input));
    }

    public <T> byte[] encryptToBinary(T input) throws Exception {
        return encodeData(publicKey, CBorUtility.toCBOR(input));
    }

    public <T> T decrypt(String encString, Class<T> clazz) throws Exception {
        return decryptFromBinary(decodeBase64(encString), clazz);
    }

    public <T> T decryptFromBinary(byte[] input, Class<T> clazz)
            throws Exception {
        byte[] decrypted = decodeData(privateKey, input);
        return CBorUtility.fromCBOR(decrypted, clazz);
    }
}
