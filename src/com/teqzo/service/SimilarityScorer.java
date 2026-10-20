package com.teqzo.service;

import com.teqzo.domain.DeviceRow;

import java.util.ArrayList;
import java.util.List;

/**
 * How alike two devices are, from 0 to 100, using only the values BOTH devices state. A missing value is never
 * guessed: it simply does not take part. Too little in common gives {@code null} instead of a misleading score.
 */
public final class SimilarityScorer {

	/** @param similarity 0-100; @param reasons short phrases for the attributes that are close. */
	public record Result(double similarity, List<String> reasons) {
	}

	static final int MIN_ATTRIBUTES = 3;
	static final double MIN_WEIGHT = 4.0;

	private SimilarityScorer() {
	}

	public static Result score(DeviceRow a, DeviceRow b) {
		double total = 0;
		double weighted = 0;
		int used = 0;
		List<String> reasons = new ArrayList<>();

		// price: close when within about 20 %; fully different at a factor of 2.5
		if (a.priceInr != null && b.priceInr != null && a.priceInr > 0 && b.priceInr > 0) {
			double c = closeness(Math.abs(Math.log(a.priceInr / b.priceInr)), Math.log(1.2), Math.log(2.5));
			total += 3;
			weighted += 3 * c;
			used++;
			if (c >= 0.85) {
				reasons.add("similar price");
			}
		}
		if (a.ramGb != null && b.ramGb != null) {
			double c = closeness(Math.abs(log2(a.ramGb) - log2(b.ramGb)), 0, 2);
			total += 2;
			weighted += 2 * c;
			used++;
			if (c >= 0.99) {
				reasons.add("same RAM (" + trim(a.ramGb) + " GB)");
			}
		}
		if (a.storageGb != null && b.storageGb != null) {
			double c = closeness(Math.abs(log2(a.storageGb) - log2(b.storageGb)), 0, 3);
			total += 1;
			weighted += c;
			used++;
			if (c >= 0.99) {
				reasons.add("same storage");
			}
		}
		if (a.batteryMah != null && b.batteryMah != null) {
			double c = closeness(Math.abs(a.batteryMah - b.batteryMah) / Math.max(a.batteryMah, b.batteryMah), 0.05, 0.5);
			total += 2;
			weighted += 2 * c;
			used++;
			if (c >= 0.85) {
				reasons.add("similar battery");
			}
		}
		if (a.displaySizeInches != null && b.displaySizeInches != null) {
			double c = closeness(Math.abs(a.displaySizeInches - b.displaySizeInches), 0.1, 1.2);
			total += 1.5;
			weighted += 1.5 * c;
			used++;
			if (c >= 0.85) {
				reasons.add("similar screen size");
			}
		}
		if (a.mainCameraMp != null && b.mainCameraMp != null) {
			double c = closeness(Math.abs(log2(a.mainCameraMp) - log2(b.mainCameraMp)), 0, 2);
			total += 1;
			weighted += c;
			used++;
			if (c >= 0.99) {
				reasons.add("same main camera resolution");
			}
		}
		Integer ya = year(a);
		Integer yb = year(b);
		if (ya != null && yb != null) {
			double c = closeness(Math.abs(ya - yb), 0, 5);
			total += 1;
			weighted += c;
			used++;
			if (c >= 0.99) {
				reasons.add("same year");
			}
		}
		String osA = family(a.os);
		String osB = family(b.os);
		if (osA != null && osB != null) {
			total += 1.5;
			weighted += osA.equals(osB) ? 1.5 : 0;
			used++;
		}
		if (used < MIN_ATTRIBUTES || total < MIN_WEIGHT) {
			return null;
		}
		return new Result(Math.round(1000.0 * weighted / total) / 10.0, reasons);
	}

	/** 1 when the difference is at most {@code same}, 0 at {@code different} or more, linear in between. */
	static double closeness(double difference, double same, double different) {
		if (difference <= same) {
			return 1;
		}
		if (difference >= different) {
			return 0;
		}
		return 1 - (difference - same) / (different - same);
	}

	private static double log2(double v) {
		return Math.log(Math.max(v, 0.1)) / Math.log(2);
	}

	private static Integer year(DeviceRow d) {
		return d.announcedYear != null ? d.announcedYear : d.listedYear;
	}

	private static String family(String os) {
		if (os == null) {
			return null;
		}
		String low = os.toLowerCase();
		if (low.startsWith("android")) {
			return "android";
		}
		if (low.startsWith("ios") || low.startsWith("ipados")) {
			return "ios";
		}
		return low.isBlank() ? null : low.split("[ ,]")[0];
	}

	private static String trim(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}
}
