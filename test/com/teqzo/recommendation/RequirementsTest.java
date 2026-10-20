package com.teqzo.recommendation;

import com.teqzo.testkit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.teqzo.testkit.Assertions.*;

class RequirementsTest {

	private static Map<String, Object> map(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
		return m;
	}

	@Test
	void mapRoundTripUsesSnakeCaseKeys() {
		Requirements r = Requirements.fromMap(map("ram_min_gb", 8, "storage_min_gb", "256", "requires_5g", true,
				"refresh_rate_min_hz", 120.0, "use_case", "gaming", "unknown_key", 1));
		assertEquals(8.0, r.ramMinGb);
		assertEquals(256.0, r.storageMinGb);
		assertEquals(Boolean.TRUE, r.requires5g);
		assertEquals(List.of(UseCase.GAMING), r.priorities);
		Map<String, Object> out = r.toMap();
		assertEquals(8.0, out.get("ram_min_gb"));
		assertFalse(out.containsKey("unknown_key"));
		assertFalse(out.containsKey("requires_nfc"));
	}

	@Test
	void falseAndWrongTypesAreNotRequirements() {
		Requirements r = Requirements.fromMap(map("requires_5g", false, "ram_min_gb", "lots", "battery_min_mah", List.of(1)));
		assertTrue(r.isEmpty());
	}

	@Test
	void laterAnswersAddToEarlierOnes() {
		Map<String, Object> first = Requirements.merge(Map.of(), map("priorities", List.of("battery")), Set.of(), false);
		Map<String, Object> second = Requirements.merge(first, map("budget_max_inr", 30000), Set.of(), false);
		Map<String, Object> third = Requirements.merge(second, map("priorities", List.of("camera")), Set.of(), false);
		Requirements r = Requirements.fromMap(third);
		assertEquals(30000.0, r.budgetMaxInr);
		assertEquals(List.of(UseCase.BATTERY, UseCase.CAMERA), r.priorities);
	}

	@Test
	void scalarsAreReplacedAndClearedOnRequest() {
		Map<String, Object> a = Requirements.merge(Map.of(), map("budget_max_inr", 30000, "ram_min_gb", 8), Set.of(), false);
		Map<String, Object> b = Requirements.merge(a, map("budget_max_inr", 20000), Set.of(), false);
		assertEquals(20000.0, b.get("budget_max_inr"));
		Map<String, Object> c = Requirements.merge(b, Map.of(), Set.of("budget_max_inr"), false);
		assertFalse(c.containsKey("budget_max_inr"));
		assertEquals(8.0, c.get("ram_min_gb"));
		assertTrue(Requirements.merge(c, Map.of(), Set.of(), true).isEmpty());
	}

	@Test
	void ignoringADimensionRemovesItsPriority() {
		Map<String, Object> a = Requirements.merge(Map.of(), map("priorities", List.of("camera", "gaming")), Set.of(), false);
		Map<String, Object> b = Requirements.merge(a, map("ignored", List.of("camera")), Set.of(), false);
		Requirements r = Requirements.fromMap(b);
		assertEquals(List.of(UseCase.GAMING), r.priorities);
		assertEquals(List.of(Dimension.CAMERA), r.ignored);
		Map<String, Object> c = Requirements.merge(b, map("priorities", List.of("camera")), Set.of(), false);
		assertTrue(Requirements.fromMap(c).ignored.isEmpty());
	}

	@Test
	void brandsAreUnioned() {
		Map<String, Object> a = Requirements.merge(Map.of(), map("brands", List.of("Samsung")), Set.of(), false);
		Map<String, Object> b = Requirements.merge(a, map("brands", "Apple, Samsung"), Set.of(), false);
		assertEquals(List.of("Samsung", "Apple"), Requirements.fromMap(b).brands);
	}
}
