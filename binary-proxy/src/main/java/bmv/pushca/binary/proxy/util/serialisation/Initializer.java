package bmv.pushca.binary.proxy.util.serialisation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.text.SimpleDateFormat;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

public final class Initializer {

    public static final String DATETIME_SECONDS_FORMAT_PATTERN =
            "yyyy-MM-dd'T'HH:mm:ssZ";

    private Initializer() {
    }

    public static JsonMapper init() {
        return init(JsonMapper.builder(), JsonInclude.Include.NON_ABSENT, false).build();
    }

    public static JsonMapper initAsStrict() {
        return init(JsonMapper.builder(), JsonInclude.Include.ALWAYS, true).build();
    }

    public static <B extends MapperBuilder<?, B>> B init(B builder,
                                                        JsonInclude.Include include,
                                                        boolean failOnUnknownProperties) {
        // Jackson 3 mappers are immutable; configure JSON and CBOR before building.
        return builder
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withValueInclusion(include)
                        .withContentInclusion(include))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .defaultDateFormat(new SimpleDateFormat(DATETIME_SECONDS_FORMAT_PATTERN));
    }
}
