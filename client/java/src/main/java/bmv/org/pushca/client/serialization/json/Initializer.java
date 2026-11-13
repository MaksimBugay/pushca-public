package bmv.org.pushca.client.serialization.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class Initializer {

  private Initializer() {
  }

  public static JsonMapper init() {
    return init(JsonInclude.Include.NON_ABSENT, false);
  }

  public static JsonMapper initAsStrict() {
    return init(JsonInclude.Include.ALWAYS, true);
  }

  public static JsonMapper init(JsonInclude.Include include,
                                boolean failOnUnknownProperties) {
    // In Jackson 3.x, ObjectMapper is immutable - use JsonMapper.builder()
    // serializationInclusion is now configured via changeDefaultPropertyInclusion
    JsonMapper.Builder builder = JsonMapper.builder()
        .changeDefaultPropertyInclusion(incl -> incl
            .withValueInclusion(include)
            .withContentInclusion(include))
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES) // Allow null -> 0/false for primitives (Jackson 2.x behavior)
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    if (failOnUnknownProperties) {
      builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    } else {
      builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    return builder.build();
  }
}
