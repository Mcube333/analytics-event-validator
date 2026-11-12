package com.mudassir.analytics;

import com.mudassir.analytics.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void parsesNestedStructuresAndTypes() {
        Map<String, Object> m = Json.asMap(Json.parse(
                "{\"a\":1,\"b\":1.5,\"c\":\"x\\u0041\",\"d\":[true,null],\"e\":{}}"));
        assertEquals(1L, m.get("a"));
        assertEquals(1.5, m.get("b"));
        assertEquals("xA", m.get("c"));
        assertEquals(java.util.Arrays.asList(true, null), m.get("d"));
        assertEquals(Map.of(), m.get("e"));
    }

    @Test
    void roundTrips() {
        Object original = Map.of("k", List.of(1L, "two \"quoted\"", false));
        assertEquals(original, Json.parse(Json.write(original)));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
    }
}
