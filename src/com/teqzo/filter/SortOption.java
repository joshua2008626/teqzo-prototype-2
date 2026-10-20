package com.teqzo.filter;

import java.util.Arrays;
import java.util.Locale;

/** Sort keys accepted by the API. The SQL expression is fixed here, never taken from user input. */
public enum SortOption {
	RELEVANCE("relevance"),
	NAME("name"),
	NEWEST("newest"),
	BATTERY_DESC("battery_desc"),
	RAM_DESC("ram_desc"),
	STORAGE_DESC("storage_desc"),
	REFRESH_DESC("refresh_desc"),
	WIRED_CHARGING_DESC("wired_charging_desc"),
	CAMERA_DESC("camera_desc"),
	DISPLAY_SIZE_DESC("display_size_desc"),
	PRICE_ASC("price_asc"),
	PRICE_DESC("price_desc"),
	WEIGHT_ASC("weight_asc");

	private final String key;

	SortOption(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public static SortOption fromKey(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		String normalised = key.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values()).filter(o -> o.key.equals(normalised)).findFirst().orElse(null);
	}

	public static String allowedKeys() {
		return String.join(", ", Arrays.stream(values()).map(SortOption::key).toList());
	}
}
