package com.teqzo.filter;

import com.teqzo.domain.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns raw query-string parameters into a validated {@link DeviceFilter}. */
public final class DeviceFilterParser {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> KNOWN = Set.of(
			"q", "name", "brand", "excludeBrand", "ramMin", "storageMin", "batteryMin", "wiredChargingMin",
			"wirelessCharging", "wirelessChargingMin", "reverseCharging", "has5g", "nfc", "ipRated",
			"displaySizeMin", "displaySizeMax", "displayType", "refreshRateMin", "chipset", "os",
			"mainCameraMin", "selfieCameraMin", "weightMax", "priceMaxInr", "priceMinInr", "releasedFrom",
			"releasedTo", "excludeCancelled", "onlyWithSpecifications", "page", "size", "sort");

	private DeviceFilterParser() {
	}

	public static DeviceFilter parse(Map<String, String> params) {
		for (String key : params.keySet()) {
			if (!KNOWN.contains(key)) {
				throw InvalidRequestException.filter("Unknown filter parameter '" + key + "'.");
			}
		}
		DeviceFilter f = new DeviceFilter();
		f.q = text(params, "q");
		if (f.q == null) {
			f.q = text(params, "name"); // backwards compatible with the first version of /search
		}
		f.brandIds = list(params, "brand");
		f.excludeBrandIds = list(params, "excludeBrand");

		f.ramMinGb = number(params, "ramMin", 0, 1024);
		f.storageMinGb = number(params, "storageMin", 0, 16384);
		f.batteryMinMah = number(params, "batteryMin", 0, 50000);
		f.wiredChargingMinW = number(params, "wiredChargingMin", 0, 1000);
		f.wirelessChargingMinW = number(params, "wirelessChargingMin", 0, 1000);
		f.wirelessCharging = onlyTrue(params, "wirelessCharging");
		f.reverseCharging = onlyTrue(params, "reverseCharging");
		f.ipRated = onlyTrue(params, "ipRated");
		f.has5g = bool(params, "has5g");
		f.nfc = bool(params, "nfc");

		f.displaySizeMinInches = number(params, "displaySizeMin", 0, 30);
		f.displaySizeMaxInches = number(params, "displaySizeMax", 0, 30);
		if (f.displaySizeMinInches != null && f.displaySizeMaxInches != null
				&& f.displaySizeMinInches > f.displaySizeMaxInches) {
			throw InvalidRequestException.filter("displaySizeMin must not be greater than displaySizeMax.");
		}
		f.displayType = text(params, "displayType");
		f.refreshRateMinHz = number(params, "refreshRateMin", 0, 1000);
		f.chipset = text(params, "chipset");
		f.os = text(params, "os");

		f.mainCameraMinMp = number(params, "mainCameraMin", 0, 500);
		f.selfieCameraMinMp = number(params, "selfieCameraMin", 0, 500);
		f.weightMaxG = number(params, "weightMax", 0, 5000);

		f.priceMaxInr = number(params, "priceMaxInr", 0, 100_000_000);
		f.priceMinInr = number(params, "priceMinInr", 0, 100_000_000);
		if (f.priceMinInr != null && f.priceMaxInr != null && f.priceMinInr > f.priceMaxInr) {
			throw InvalidRequestException.filter("priceMinInr must not be greater than priceMaxInr.");
		}

		f.releasedFromYear = year(params, "releasedFrom");
		f.releasedToYear = year(params, "releasedTo");
		if (f.releasedFromYear != null && f.releasedToYear != null && f.releasedFromYear > f.releasedToYear) {
			throw InvalidRequestException.filter("releasedFrom must not be after releasedTo.");
		}
		f.excludeCancelled = Boolean.TRUE.equals(bool(params, "excludeCancelled"));
		f.onlyWithSpecifications = Boolean.TRUE.equals(bool(params, "onlyWithSpecifications"));

		f.page = (int) intValue(params, "page", 0, 0, 1_000_000);
		f.size = (int) intValue(params, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
		String sortKey = params.get("sort");
		if (sortKey != null && !sortKey.isBlank()) {
			f.sort = SortOption.fromKey(sortKey);
			if (f.sort == null) {
				throw InvalidRequestException.filter(
						"Unknown sort '" + sortKey + "'. Allowed values: " + SortOption.allowedKeys() + ".");
			}
			if (f.sort == SortOption.RELEVANCE && f.q == null) {
				f.sort = null; // relevance needs a search text; fall back to the default order
			}
		}
		return f;
	}

	private static String text(Map<String, String> p, String key) {
		String v = p.get(key);
		if (v == null || v.isBlank()) {
			return null;
		}
		String trimmed = v.trim();
		if (trimmed.length() > 100) {
			throw InvalidRequestException.filter("Parameter '" + key + "' is too long.");
		}
		return trimmed;
	}

	private static List<String> list(Map<String, String> p, String key) {
		String v = text(p, key);
		if (v == null) {
			return null;
		}
		List<String> out = new ArrayList<>();
		for (String part : v.split(",")) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		if (out.size() > 20) {
			throw InvalidRequestException.filter("Parameter '" + key + "' accepts at most 20 values.");
		}
		return out.isEmpty() ? null : out;
	}

	private static Double number(Map<String, String> p, String key, double min, double max) {
		String v = text(p, key);
		if (v == null) {
			return null;
		}
		double d;
		try {
			d = Double.parseDouble(v);
		} catch (NumberFormatException e) {
			throw InvalidRequestException.filter("Parameter '" + key + "' must be a number.");
		}
		if (Double.isNaN(d) || d < min || d > max) {
			throw InvalidRequestException.filter(
					"Parameter '" + key + "' must be between " + trim(min) + " and " + trim(max) + ".");
		}
		return d;
	}

	private static long intValue(Map<String, String> p, String key, long defaultValue, long min, long max) {
		String v = text(p, key);
		if (v == null) {
			return defaultValue;
		}
		long n;
		try {
			n = Long.parseLong(v);
		} catch (NumberFormatException e) {
			throw InvalidRequestException.filter("Parameter '" + key + "' must be a whole number.");
		}
		if (n < min || n > max) {
			throw InvalidRequestException.filter(
					"Parameter '" + key + "' must be between " + min + " and " + max + ".");
		}
		return n;
	}

	private static Integer year(Map<String, String> p, String key) {
		String v = text(p, key);
		return v == null ? null : (int) intValue(p, key, 0, 1990, 2100);
	}

	private static Boolean bool(Map<String, String> p, String key) {
		String v = text(p, key);
		if (v == null) {
			return null;
		}
		switch (v.toLowerCase(Locale.ROOT)) {
			case "true":
			case "1":
			case "yes":
				return Boolean.TRUE;
			case "false":
			case "0":
			case "no":
				return Boolean.FALSE;
			default:
				throw InvalidRequestException.filter("Parameter '" + key + "' must be true or false.");
		}
	}

	/**
	 * Some filters can only express "the dataset says it is supported". Asking for "not supported" would
	 * require treating missing data as a negative, which this API never does.
	 */
	private static Boolean onlyTrue(Map<String, String> p, String key) {
		Boolean b = bool(p, key);
		if (Boolean.FALSE.equals(b)) {
			throw InvalidRequestException.filter(
					"Parameter '" + key + "' only supports true. Missing data is never treated as 'not supported'.");
		}
		return b;
	}

	private static String trim(double d) {
		return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
	}
}
