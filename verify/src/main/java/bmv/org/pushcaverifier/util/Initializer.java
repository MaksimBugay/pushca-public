package bmv.org.pushcaverifier.util;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.text.SimpleDateFormat;

public final class Initializer {

    public static final String DATETIME_SECONDS_FORMAT_PATTERN =
            "yyyy-MM-dd'T'HH:mm:ssZ";

    private Initializer() {
    }

    public static ObjectMapper init(ObjectMapper mapper) {
        return init(mapper, JsonInclude.Include.NON_ABSENT, false);
    }

    public static ObjectMapper init(ObjectMapper mapper,
                                    JsonInclude.Include include,
                                    boolean failOnUnknownProperties) {
        return mapper
                .setSerializationInclusion(include)
                .setDefaultPropertyInclusion(include)
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .registerModule(new ParameterNamesModule())
                .configure(FAIL_ON_EMPTY_BEANS, false)
                .configure(FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                .configure(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                .configure(WRITE_DATES_AS_TIMESTAMPS, false)
                .setDateFormat(
                        new SimpleDateFormat(DATETIME_SECONDS_FORMAT_PATTERN));
    }
}
