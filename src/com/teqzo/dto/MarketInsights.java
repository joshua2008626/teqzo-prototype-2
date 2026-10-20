package com.teqzo.dto;

import java.util.List;
import java.util.Map;

/** Summary figures computed by the database with GROUP BY queries. */
public record MarketInsights(List<BrandStat> brands, List<YearCount> years, List<PriceBand> priceBands,
		Map<String, Long> devicesBySource) {

	/** Averages are over the devices that state the value; {@code null} when none does. */
	public record BrandStat(String brand, long devices, Double avgPriceInr, Double avgBatteryMah, Double avgRamGb) {
	}

	public record YearCount(int year, long devices) {
	}

	public record PriceBand(String label, long devices) {
	}

	public static MarketInsights empty() {
		return new MarketInsights(List.of(), List.of(), List.of(), Map.of());
	}
}
