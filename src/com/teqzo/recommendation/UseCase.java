package com.teqzo.recommendation;

import java.util.Locale;

/** What the user mainly cares about. Each use case maps to a documented weight profile. */
public enum UseCase {
	BALANCED, GAMING, CAMERA, BATTERY, DISPLAY, PERFORMANCE;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static UseCase fromKey(String key) {
		if (key == null) {
			return null;
		}
		String k = key.trim().toLowerCase(Locale.ROOT);
		for (UseCase u : values()) {
			if (u.key().equals(k)) {
				return u;
			}
		}
		return null;
	}
}
