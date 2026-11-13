package bmv.org.pushca.client.serialization.json;

import static java.util.stream.Collectors.toMap;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.MapType;
import bmv.org.pushca.client.exception.BmvSerializationException;

@SuppressWarnings("unused")
public final class JsonUtility {

  private static final JsonMapper SIMPLE_MAPPER = getSimpleMapperInstance();
  public static final MapType MAP_TYPE = SIMPLE_MAPPER.getTypeFactory().constructMapType(Map.class, String.class,
      Object.class);
  public static final MapType STRING_MAP_TYPE = SIMPLE_MAPPER.getTypeFactory().constructMapType(Map.class, String.class,
      String.class);
  private static final JsonMapper WITH_NULL_MAPPER = getWithNullMapperInstance();
  private static final String EMPTY_JSON = "{}";

  private JsonUtility() {
  }

  public static JsonMapper getSimpleMapperInstance() {
    return Initializer.init();
  }

  public static JsonMapper getWithNullMapperInstance() {
    return Initializer.initAsStrict();
  }

  public static String prettyPrintJson(String json, boolean writeNulls) {
    try {
      if (writeNulls) {
        Object object = WITH_NULL_MAPPER.readValue(json, Object.class);
        return WITH_NULL_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
      } else {
        Object object = SIMPLE_MAPPER.readValue(json, Object.class);
        return SIMPLE_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
      }
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static String toJson(Object object) {
    return toJson(object, false);
  }

  public static String toJson(Object object, boolean writeNulls) {
    try {
      if (writeNulls) {
        return WITH_NULL_MAPPER.writeValueAsString(object);
      } else {
        return SIMPLE_MAPPER.writeValueAsString(object);
      }
    } catch (Exception jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static byte[] toJsonAsBytes(Object object) {
    return toJsonAsBytes(object, false);
  }

  public static byte[] toJsonAsBytes(Object object, boolean writeNulls) {
    try {
      if (writeNulls) {
        return WITH_NULL_MAPPER.writeValueAsBytes(object);
      } else {
        return SIMPLE_MAPPER.writeValueAsBytes(object);
      }
    } catch (Exception jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJson(byte[] jsonBytes, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(jsonBytes, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJsonStrict(byte[] jsonBytes, Class<T> clazz) {
    try {
      return WITH_NULL_MAPPER.readValue(jsonBytes, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJson(String jsonString, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(jsonString, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJsonStrict(String jsonString, Class<T> clazz) {
    try {
      return WITH_NULL_MAPPER.readValue(jsonString, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJson(InputStream is, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(is, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJsonStrict(InputStream is, Class<T> clazz) {
    try {
      return WITH_NULL_MAPPER.readValue(is, clazz);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> T fromJsonWithTypeReference(String jsonString, JavaType resolvedType) {
    try {
      return SIMPLE_MAPPER.readValue(jsonString, resolvedType);
    } catch (JacksonException jpe) {
      throw new BmvSerializationException(jpe);
    }
  }

  public static <T> Set<T> fromJsonToSet(String jsonInput, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(jsonInput,
          SIMPLE_MAPPER.getTypeFactory().constructCollectionType(Set.class, clazz));
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static <T> Set<T> fromJsonToSet(byte[] jsonInput, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(jsonInput,
          SIMPLE_MAPPER.getTypeFactory().constructCollectionType(Set.class, clazz));
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static <K, V> Map<K, V> fromJsonToMap(byte[] jsonInput, Class<K> clazzKey, Class<V> clazzValue) {
    try {
      return SIMPLE_MAPPER.readValue(jsonInput,
          SIMPLE_MAPPER.getTypeFactory().constructMapType(Map.class, clazzKey, clazzValue));
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static <T> List<T> fromJsonToList(String jsonInput, Class<T> clazz) {
    try {
      return SIMPLE_MAPPER.readValue(jsonInput,
          SIMPLE_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static <T> List<T> fromJsonToList(String jsonInput, JavaType javaType) {
    try {
      return SIMPLE_MAPPER.readValue(jsonInput,
          SIMPLE_MAPPER.getTypeFactory().constructCollectionType(List.class, javaType));
    } catch (JacksonException e) {
      throw new BmvSerializationException(e);
    }
  }

  public static Map<String, Object> convertToMap(Object content) {
    String jsonString = JsonUtility.toJson(content);
    return JsonUtility.fromJsonWithTypeReference(jsonString, MAP_TYPE);
  }

  public static <T> T convertToObjectFromMap(Map<String, ?> map, Class<T> clazz) {
    return SIMPLE_MAPPER.convertValue(map, clazz);
  }

  public static Map<String, Object> convertToFilteredMap(Object content, List<String> filter) {
    if (content == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> filteredMap = convertToMap(content);
    return filteredMap.entrySet().stream().filter(entry -> filter.contains(entry.getKey()))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static String emptyJson() {
    return EMPTY_JSON;
  }

  public static JsonMapper getSimpleMapper() {
    return SIMPLE_MAPPER;
  }

  public static JsonMapper getWithNullMapper() {
    return WITH_NULL_MAPPER;
  }

  public static boolean isValid(String json) {
    try {
      SIMPLE_MAPPER.readTree(json);
    } catch (JacksonException e) {
      return false;
    }
    return true;
  }
}
