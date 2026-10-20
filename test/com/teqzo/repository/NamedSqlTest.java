package com.teqzo.repository;

import com.teqzo.testkit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class NamedSqlTest {

	@Test
	void replacesNamesWithMarkersInOrder() {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("a", 1);
		p.put("b", "x");
		NamedSql.Prepared r = NamedSql.prepare("SELECT * FROM t WHERE a = :a AND b = :b AND c = :a", p);
		assertEquals("SELECT * FROM t WHERE a = ? AND b = ? AND c = ?", r.sql());
		assertEquals(List.of(1, "x", 1), r.values());
	}

	@Test
	void expandsLists() {
		NamedSql.Prepared r = NamedSql.prepare("WHERE id IN (:ids) AND n > :n", Map.of("ids", List.of("a", "b", "c"), "n", 5));
		assertEquals("WHERE id IN (?, ?, ?) AND n > ?", r.sql());
		assertEquals(List.of("a", "b", "c", 5), r.values());
	}

	@Test
	void leavesQuotedTextAndDoubleColonAlone() {
		NamedSql.Prepared r = NamedSql.prepare("SELECT ':x', a::int FROM t WHERE b = :b", Map.of("b", 2));
		assertEquals("SELECT ':x', a::int FROM t WHERE b = ?", r.sql());
		assertEquals(List.of(2), r.values());
	}

	@Test
	void rejectsMissingAndEmptyValues() {
		assertThrows(IllegalArgumentException.class, () -> NamedSql.prepare("WHERE a = :a", Map.of()));
		assertThrows(IllegalArgumentException.class, () -> NamedSql.prepare("WHERE a IN (:a)", Map.of("a", List.of())));
	}

	@Test
	void valuesAreNeverPastedIntoSql() {
		NamedSql.Prepared r = NamedSql.prepare("WHERE a = :a", Map.of("a", "x'; DROP TABLE device; --"));
		assertEquals("WHERE a = ?", r.sql());
	}
}
