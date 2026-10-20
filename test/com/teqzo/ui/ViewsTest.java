package com.teqzo.ui;

import com.teqzo.dto.CountryPrice;
import com.teqzo.dto.MarketInsights;
import com.teqzo.testkit.Test;

import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class ViewsTest {

	@Test
	void regionalPricesShowRangeAndVariants() {
		List<String> lines = Views.regionalPrices(List.of(new CountryPrice("India", "INR", 69999, 84999, 3),
				new CountryPrice("USA", "USD", 799, 799, 1)));
		assertTrue(lines.get(0).startsWith("LAUNCH PRICES BY COUNTRY"));
		assertTrue(lines.get(1).contains("INR 69,999 - 84,999") && lines.get(1).contains("3 variants"));
		assertTrue(lines.get(2).trim().endsWith("USD 799"));
		assertTrue(Views.regionalPrices(List.of()).isEmpty());
	}

	@Test
	void rupeeSignBecomesRsSoColumnsStayAligned() {
		assertEquals("Rs 1,29,999", Views.rs("₹ 1,29,999"));
		assertEquals("EUR 949", Views.rs("€949"));
	}

	@Test
	void barsAreProportionalAndNeverEmptyForPositiveValues() {
		assertEquals("##########", Views.bar(100, 100, 10));
		assertEquals("#####", Views.bar(50, 100, 10));
		assertEquals("#", Views.bar(1, 1000, 10));
		assertEquals("", Views.bar(0, 100, 10));
		assertEquals("", Views.bar(5, 0, 10));
	}

	@Test
	void insightsHandleMissingAverages() {
		MarketInsights m = new MarketInsights(List.of(new MarketInsights.BrandStat("Acme", 5, null, 4000.0, null)),
				List.of(new MarketInsights.YearCount(2024, 3)), List.of(new MarketInsights.PriceBand("under 10,000", 2)),
				Map.of());
		String text = String.join("\n", Views.insights(m));
		assertTrue(text.contains("Acme"));
		assertTrue(text.contains("4000 mAh"));
		assertTrue(text.contains("2024"));
	}
}
