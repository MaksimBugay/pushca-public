package bmv.pushca.binary.proxy.util.serialisation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SerializationTest {

    record Payload(String name, int count, Optional<String> optional, LocalDateTime timestamp) {}

    @Test
    void preservesNullAndOptionalInclusion() {
        Payload payload = new Payload(null, 1, Optional.empty(), null);
        assertEquals("{\"count\":1}", JsonUtility.toJson(payload));
        Map<?, ?> withNulls = JsonUtility.fromJson(JsonUtility.toJson(payload, true), Map.class);
        assertEquals(4, withNulls.size());
        assertNull(withNulls.get("name"));
        assertNull(withNulls.get("optional"));
    }

    @Test
    void preservesLenientAndStrictDeserialization() {
        String json = "{\"name\":\"test\",\"count\":null,\"unknown\":true}";
        assertEquals(0, JsonUtility.fromJson(json, Payload.class).count());
        assertThrows(RuntimeException.class, () -> JsonUtility.fromJsonStrict(json, Payload.class));
        assertEquals(0, JsonUtility.fromJsonStrict("{\"count\":null}", Payload.class).count());
        assertEquals("test", JsonUtility.fromJson(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Payload.class).name());
    }

    @Test
    void roundTripsDatesAndTypedCollections() {
        Payload payload = new Payload("test", 2, Optional.of("value"),
                LocalDateTime.of(2026, 9, 7, 12, 30));
        String json = JsonUtility.toJson(payload);
        assertTrue(json.contains("2026-09-07T12:30:00"));
        assertEquals(payload, JsonUtility.fromJson(json, Payload.class));
        assertEquals(List.of(payload), JsonUtility.fromJsonToList("[" + json + "]", Payload.class));
        String legacyDate = new java.text.SimpleDateFormat(
                Initializer.DATETIME_SECONDS_FORMAT_PATTERN).format(new Date(0));
        assertEquals("\"" + legacyDate + "\"", JsonUtility.toJson(new Date(0)));
        assertEquals(new Date(0), JsonUtility.fromJson(JsonUtility.toJson(new Date(0)), Date.class));
    }

    @Test
    void wrapsMalformedInputAndChecksValidity() {
        assertThrows(RuntimeException.class, () -> JsonUtility.fromJson("{", Payload.class));
        assertFalse(JsonUtility.isValid("{"));
        assertTrue(JsonUtility.isValid("{\"value\":1}"));
    }

    @Test
    void deserializesCborListWithTypedElementsInOrder() {
        List<Payload> expected = List.of(
                new Payload("first", 2, Optional.of("value"),
                        LocalDateTime.of(2026, 9, 7, 12, 30)),
                new Payload("second", 5, Optional.empty(), null));

        List<Payload> actual = CBorUtility.fromCBORToList(
                CBorUtility.toCBOR(expected), Payload.class);

        assertEquals(expected, actual);
    }

    @Test
    void deserializesEmptyCborList() {
        assertEquals(List.of(), CBorUtility.fromCBORToList(
                CBorUtility.toCBOR(List.of()), Payload.class));
    }

    @Test
    void rejectsTruncatedCborList() {
        // An array declaring one element, but missing that element.
        byte[] truncated = {(byte) 0x81};

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CBorUtility.fromCBORToList(truncated, Payload.class));

        assertInstanceOf(tools.jackson.core.JacksonException.class, exception.getCause());
    }

    @Test
    void preservesCborHeaderAndReadsExistingWireFormat() {
        // Jackson 2-compatible self-described CBOR map: {"name":"test","count":2}.
        byte[] existing = java.util.HexFormat.of().parseHex("d9d9f7bf646e616d65647465737465636f756e7402ff");
        assertTrue(CBorUtility.isCbor(existing));
        Payload decoded = CBorUtility.fromCBOR(existing, Payload.class);
        assertEquals("test", decoded.name());
        assertEquals(2, decoded.count());
        Payload payload = new Payload("test", 2, Optional.of("value"),
                LocalDateTime.of(2026, 9, 7, 12, 30));
        byte[] encoded = CBorUtility.toCBOR(payload);
        assertTrue(CBorUtility.isCbor(encoded));
        assertEquals(payload, CBorUtility.fromCBOR(encoded, Payload.class));
        var type = CBorUtility.getTypeFactory().constructCollectionType(List.class, Payload.class);
        assertEquals(List.of(payload), CBorUtility.fromCBORWithTypeReference(
                CBorUtility.toCBOR(List.of(payload)), type));
    }
}
