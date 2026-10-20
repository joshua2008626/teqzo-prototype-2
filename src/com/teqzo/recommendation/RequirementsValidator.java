package com.teqzo.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Backend validation of requirements, whoever produced them (client, LLM or rule parser).
 * Implausible values are dropped with a note instead of silently producing an empty result.
 */
public final class RequirementsValidator {

	public record Validated(Requirements requirements, List<String> notes) {
	}

	private static final Set<String> DISPLAY_TYPES = Set.of("amoled", "oled", "ips", "lcd", "ltpo", "tft");
	static final int MAX_BRANDS = 10;

	private RequirementsValidator() {
	}

	public static Validated validate(Requirements input) {
		Requirements r = input.copy();
		List<String> notes = new ArrayList<>();

		r.ramMinGb = range(r.ramMinGb, 0.25, 1024, "RAM", notes);
		r.storageMinGb = range(r.storageMinGb, 1, 16384, "storage", notes);
		r.batteryMinMah = range(r.batteryMinMah, 500, 30000, "battery capacity", notes);
		r.wiredChargingMinW = range(r.wiredChargingMinW, 1, 1000, "wired charging speed", notes);
		r.refreshRateMinHz = range(r.refreshRateMinHz, 30, 500, "refresh rate", notes);
		r.displaySizeMinInches = range(r.displaySizeMinInches, 2, 30, "minimum display size", notes);
		r.displaySizeMaxInches = range(r.displaySizeMaxInches, 2, 30, "maximum display size", notes);
		if (r.displaySizeMinInches != null && r.displaySizeMaxInches != null
				&& r.displaySizeMinInches > r.displaySizeMaxInches) {
			notes.add("Ignored the maximum display size because it is smaller than the minimum.");
			r.displaySizeMaxInches = null;
		}
		r.mainCameraMinMp = range(r.mainCameraMinMp, 1, 500, "main camera resolution", notes);
		r.maxWeightG = range(r.maxWeightG, 20, 5000, "maximum weight", notes);
		r.budgetMaxInr = range(r.budgetMaxInr, 1000, 100_000_000, "budget", notes);
		if (r.minReleaseYear != null && (r.minReleaseYear < 1995 || r.minReleaseYear > 2100)) {
			notes.add("Ignored an implausible release year (" + r.minReleaseYear + ").");
			r.minReleaseYear = null;
		}

		if (r.displayType != null) {
			String t = r.displayType.toLowerCase(Locale.ROOT);
			r.displayType = DISPLAY_TYPES.contains(t) ? t : null;
			if (r.displayType == null) {
				notes.add("Ignored the unsupported display type \"" + input.displayType + "\".");
			}
		}
		if (r.os != null) {
			String o = r.os.toLowerCase(Locale.ROOT);
			if (o.contains("android")) {
				r.os = "android";
			} else if (o.equals("ios") || o.contains("iphone") || o.contains("apple")) {
				r.os = "ios";
			} else {
				notes.add("Ignored the unsupported operating system \"" + input.os + "\".");
				r.os = null;
			}
		}
		r.brands = limit(r.brands, notes);
		r.excludeBrands = limit(r.excludeBrands, notes);
		return new Validated(r, notes);
	}

	private static List<String> limit(List<String> values, List<String> notes) {
		if (values.size() > MAX_BRANDS) {
			notes.add("Only the first " + MAX_BRANDS + " brands were used.");
			return new ArrayList<>(values.subList(0, MAX_BRANDS));
		}
		return values;
	}

	private static Double range(Double value, double min, double max, String label, List<String> notes) {
		if (value == null) {
			return null;
		}
		if (value < min || value > max) {
			notes.add("Ignored the " + label + " requirement (" + format(value) + ") because it is outside"
					+ " the plausible range.");
			return null;
		}
		return value;
	}

	private static String format(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}
}
