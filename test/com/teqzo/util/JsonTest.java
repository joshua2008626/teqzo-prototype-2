package com.teqzo.util;

import com.teqzo.testkit.Test;

import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class JsonTest {

	@Test
	void parsesNestedStructures() {
		Map<String, Object> m = Json.asObject(Json.parse("{\"a\":1,\"b\":[true,null,\"x\\n\\u00e9\"],\"c\":{\"d\":2.5}}"));
		assertEquals(1L, m.get("a"));
		assertEquals(3, Json.asArray(m.get("b")).size());
		assertEquals(2.5, Json.path(m, "c", "d"));
		assertEquals("x\né", Json.path(m, "b", 2));
	}

	@Test
	void roundTripsAndEscapes() {
		String text = Json.stringify(Map.of("k", "quote\" back\\ line\n"));
		assertEquals("quote\" back\\ line\n", Json.path(Json.parse(text), "k"));
		assertEquals("[1,2.5,null]", Json.stringify(java.util.Arrays.asList(1, 2.5, null)));
		assertEquals("30000", Json.stringify(30000.0));
	}

	@Test
	void lenientParseFindsObjectInsideFences() {
		Map<String, Object> m = Json.parseObjectLenient("Sure!\n```json\n{\"updates\": {\"ram_min_gb\": 8}}\n```");
		assertEquals(8L, Json.path(m, "updates", "ram_min_gb"));
	}

	@Test
	void rejectsInvalidJson() {
		assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
		assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1} trailing"));
		assertThrows(Json.JsonException.class, () -> Json.parseObjectLenient("no json here"));
	}
}
