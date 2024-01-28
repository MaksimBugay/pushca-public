package bmv.org.pushca.client.transformation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;

public interface BinaryPayloadTransformer {

  default Function<String, byte[]> getEncoder() {
    return s -> Base64.getEncoder().encode(s.getBytes(StandardCharsets.UTF_8));
  }

  default Function<byte[], String> getDecoder() {
    return bytes -> new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8);
  }
}
