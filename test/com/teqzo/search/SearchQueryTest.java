package com.teqzo.search;

import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class SearchQueryTest {

	@Test
	void normalisesCaseAndPunctuation() {
		SearchQuery q = SearchQuery.parse("  Galaxy-S24  Ultra! ");
		assertEquals(List.of("galaxy", "s24", "ultra"), q.tokens());
		assertEquals("galaxys24ultra", q.compact());
	}

	@Test
	void spacingVariantsShareTheSameCompactForm() {
		assertEquals(SearchQuery.parse("iPhone 15").compact(), SearchQuery.parse("iphone15").compact());
	}

	@Test
	void emptyOrSymbolOnlyInputIsNull() {
		assertNull(SearchQuery.parse(null));
		assertNull(SearchQuery.parse("   "));
		assertNull(SearchQuery.parse("%%%"));
	}

	@Test
	void limitsTokensAndLength() {
		SearchQuery q = SearchQuery.parse("a b c d e f g h i j k");
		assertEquals(SearchQuery.MAX_TOKENS, q.tokens().size());
		assertEquals(SearchQuery.MAX_TOKEN_LENGTH, SearchQuery.parse("x".repeat(100)).tokens().get(0).length());
	}

	@Test
	void shortNumericTokensAreNotMatchedAgainstChipsets() {
		assertFalse(SearchQuery.matchesSpecText("7"));
		assertFalse(SearchQuery.matchesSpecText("22"));
		assertTrue(SearchQuery.matchesSpecText("888"));
		assertTrue(SearchQuery.matchesSpecText("snapdragon"));
	}
}
