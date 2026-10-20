package com.teqzo.recommendation;

import java.util.Locale;

/** The aspects of a device the engine can score. */
public enum Dimension {
	PERFORMANCE("Performance"),
	MEMORY("Memory & storage"),
	BATTERY("Battery"),
	CHARGING("Charging"),
	CAMERA("Camera"),
	DISPLAY("Display"),
	CONNECTIVITY("Connectivity"),
	FRESHNESS("Recency");

	private final String label;

	Dimension(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Dimension fromKey(String key) {
		if (key == null) {
			return null;
		}
		String k = key.trim().toLowerCase(Locale.ROOT);
		for (Dimension d : values()) {
			if (d.key().equals(k)) {
				return d;
			}
		}
		return null;
	}
}
