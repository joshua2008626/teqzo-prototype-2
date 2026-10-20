package com.teqzo.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ready-made "top lists". Each one is just a filter + sort expressed with the same parameter names the filter parser
 * understands, so the result list works exactly like a normal search. Only devices that STATE the ranked value are
 * included (an unknown battery never ranks as the best battery).
 */
public enum Leaderboard {
	BATTERY("Biggest battery", "battery_desc", "batteryMin", "1"),
	RAM("Most RAM", "ram_desc", "ramMin", "1"),
	CAMERA("Highest main camera resolution (megapixels only; not a quality measure)", "camera_desc", "mainCameraMin", "1"),
	LIGHTEST("Lightest phones", "weight_asc", "weightMax", "1000"),
	CHARGING("Fastest wired charging", "wired_charging_desc", "wiredChargingMin", "1"),
	CHEAPEST("Lowest listed price (INR)", "price_asc", "priceMinInr", "1"),
	NEWEST("Newest", "newest", null, null);

	private final String title;
	private final String sort;
	private final String requiredKey;
	private final String requiredValue;

	Leaderboard(String title, String sort, String requiredKey, String requiredValue) {
		this.title = title;
		this.sort = sort;
		this.requiredKey = requiredKey;
		this.requiredValue = requiredValue;
	}

	public String title() {
		return title;
	}

	/**
	 * Filter parameters for this list. Only phone-sized screens (4.0-8.5 inches) with specification data are
	 * ranked, because the data also contains smartwatches and tablets.
	 *
	 * @param budgetInr optional upper price limit in rupees, may be null
	 */
	public Map<String, String> params(Double budgetInr) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("onlyWithSpecifications", "true");
		p.put("excludeCancelled", "true");
		p.put("displaySizeMin", "4");
		p.put("displaySizeMax", "8.5");
		if (requiredKey != null) {
			p.put(requiredKey, requiredValue);
		}
		if (budgetInr != null) {
			p.put("priceMaxInr", String.valueOf(budgetInr.longValue()));
		}
		p.put("sort", sort);
		return p;
	}
}
