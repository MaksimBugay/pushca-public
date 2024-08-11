package bmv.pushca.binary.proxy.util.serialisation;

import static bmv.pushca.binary.proxy.util.serialisation.Initializer.init;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public final class CBorUtility {

    public static final String SELF_DESCRIBE_CBOR = "d9d9f7";

    public static final ObjectMapper CBOR_OBJECT_MAPPER =
            getCborMapperInstance();

    private CBorUtility() {
    }

    public static ObjectMapper getCborMapperInstance() {
        return init(new ObjectMapper(new CBORFactory().configure(
                CBORGenerator.Feature.WRITE_TYPE_HEADER, true)));
    }

    public static boolean isCbor(byte[] bytes) {
        return SELF_DESCRIBE_CBOR.equals(getFirstTag(bytes));
    }

    public static TypeFactory getTypeFactory() {
        return CBOR_OBJECT_MAPPER.getTypeFactory();
    }

    public static byte[] toCBOR(Object obj) throws JsonProcessingException {
        return CBOR_OBJECT_MAPPER.writeValueAsBytes(obj);
    }

    public static <T> T fromCBOR(byte[] cbor, Class<T> clazz)
            throws IOException {
        return CBOR_OBJECT_MAPPER.readValue(cbor, clazz);
    }

    public static <T> T fromCBORWithTypeReference(byte[] cbor,
                                                  JavaType resolvedType) {
        try {
            return CBOR_OBJECT_MAPPER.readValue(cbor, resolvedType);
        } catch (IOException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    public static <T> List<T> fromCBORToList(byte[] cbor, Class<T> clazz) {
        try {
            return CBOR_OBJECT_MAPPER.readValue(cbor,
                    CBOR_OBJECT_MAPPER.getTypeFactory()
                            .constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getFirstTag(byte[] bytes) {
        return encodeHexString(ArrayUtils.subarray(bytes, 0, 3));
    }
}
